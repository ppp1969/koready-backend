package koready_backend.kto.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoDuplicateContentIdException;
import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.model.KtoStorePageCommand;
import koready_backend.kto.application.model.KtoStorePageResult;
import koready_backend.kto.application.port.KtoPageStore;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class KtoPageJdbcStore implements KtoPageStore {

	private static final String PROVIDER = "KTO";
	private static final String API_NAME = "KOR";
	private static final String OPERATION = "areaBasedSyncList2";
	private static final String ENDPOINT =
		"https://apis.data.go.kr/B551011/KorService2/areaBasedSyncList2";
	private static final String MATCHER_VERSION = "kto-ko-content-id-v1";
	private static final DateTimeFormatter KTO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private static final String UPSERT_PLACE_SQL = """
		INSERT INTO places
			(kto_content_id, kto_content_type_id, service_region_code,
			 area_code, sigungu_code, ldong_regn_cd, ldong_signgu_cd,
			 lcls_systm1, lcls_systm2, lcls_systm3, address,
			 latitude, longitude, tel, first_image_url, source_modified_time,
			 show_flag, active)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			kto_content_type_id = VALUES(kto_content_type_id),
			service_region_code = VALUES(service_region_code),
			area_code = VALUES(area_code),
			sigungu_code = VALUES(sigungu_code),
			ldong_regn_cd = VALUES(ldong_regn_cd),
			ldong_signgu_cd = VALUES(ldong_signgu_cd),
			lcls_systm1 = VALUES(lcls_systm1),
			lcls_systm2 = VALUES(lcls_systm2),
			lcls_systm3 = VALUES(lcls_systm3),
			address = VALUES(address),
			latitude = VALUES(latitude),
			longitude = VALUES(longitude),
			tel = VALUES(tel),
			first_image_url = VALUES(first_image_url),
			source_modified_time = VALUES(source_modified_time),
			show_flag = VALUES(show_flag),
			active = VALUES(active)
		""";

	private static final String UPSERT_LOCALIZATION_SQL = """
		INSERT INTO place_localizations
			(place_id, language, title, address_text, translation_source,
			 source_content_id, source_hash)
		VALUES (?, 'KO', ?, ?, 'KTO_KO', ?, ?)
		ON DUPLICATE KEY UPDATE
			title = VALUES(title),
			address_text = VALUES(address_text),
			translation_source = 'KTO_KO',
			source_content_id = VALUES(source_content_id),
			source_hash = VALUES(source_hash)
		""";

	private static final String INSERT_SOURCE_RECORD_SQL = """
		INSERT INTO place_source_records
			(provider, api_name, operation, source_content_id, language,
			 raw_snapshot_id, source_modified_time, source_hash, captured_at)
		VALUES ('KTO', 'KOR', 'areaBasedSyncList2', ?, 'KO', ?, ?, ?, ?)
		""";

	private static final String INSERT_SOURCE_MATCH_SQL = """
		INSERT INTO place_source_matches
			(source_record_id, place_id, match_method, confidence,
			 candidate_count, evidence_json, status, matcher_version)
		VALUES (?, ?, 'CONTENT_ID', 1.0000, 1, ?, 'AUTO_CONFIRMED', ?)
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;
	private final KtoBatchProperties batchProperties;

	public KtoPageJdbcStore(
		JdbcTemplate jdbcTemplate,
		JsonMapper jsonMapper,
		KtoBatchProperties batchProperties
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
		this.batchProperties = batchProperties;
	}

	@Override
	@Transactional
	public KtoStorePageResult store(KtoStorePageCommand command) {
		Objects.requireNonNull(command, "KTO page storage command is required");
		ExistingSnapshot existingSnapshot = findExistingSnapshot(command.snapshot().storageKey());
		if (existingSnapshot != null) {
			return replayExisting(command, existingSnapshot);
		}

		validateUniqueContentIds(command.page().items());
		long callLogId = insertCallLog(command);
		long snapshotId = insertSnapshot(command, callLogId);
		Map<String, String> serviceRegions = loadServiceRegions();
		List<PlaceRow> places = command.page().items().stream()
			.map(item -> toPlaceRow(
				item,
				item.areaCode() == null ? null : serviceRegions.get(item.areaCode())))
			.toList();

		upsertPlaces(places);
		Map<String, Long> placeIds = loadPlaceIds(places);
		upsertLocalizations(places, placeIds);
		insertSourceRecords(command, snapshotId, places);
		Map<String, Long> sourceRecordIds = loadSourceRecordIds(snapshotId);
		insertSourceMatches(places, placeIds, sourceRecordIds);
		advancePageCursor(command);

		int activeCount = (int) places.stream().filter(PlaceRow::active).count();
		int localizationCount = (int) places.stream().filter(place -> place.title() != null).count();
		return new KtoStorePageResult(
			callLogId,
			snapshotId,
			places.size(),
			activeCount,
			localizationCount,
			false);
	}

	private ExistingSnapshot findExistingSnapshot(String storageKey) {
		List<ExistingSnapshot> snapshots = jdbcTemplate.query(
			"""
			SELECT id, call_log_id, raw_content_sha256, stored_object_sha256,
			       byte_size, compressed_byte_size, item_count
			FROM open_api_raw_snapshots
			WHERE storage_key = ?
			""",
			(rs, rowNumber) -> new ExistingSnapshot(
				rs.getLong("id"),
				rs.getLong("call_log_id"),
				rs.getString("raw_content_sha256"),
				rs.getString("stored_object_sha256"),
				rs.getLong("byte_size"),
				rs.getLong("compressed_byte_size"),
				rs.getInt("item_count")),
			storageKey);
		return snapshots.isEmpty() ? null : snapshots.getFirst();
	}

	private KtoStorePageResult replayExisting(
		KtoStorePageCommand command,
		ExistingSnapshot existing
	) {
		if (!existing.rawContentSha256().equals(command.page().responseSha256())
			|| !existing.storedObjectSha256().equals(command.snapshot().storedObjectSha256())
			|| existing.byteSize() != command.page().responseBytes()
			|| existing.compressedByteSize() != command.snapshot().compressedByteSize()
			|| existing.itemCount() != command.page().items().size()) {
			throw new KtoSnapshotConflictException();
		}

		int activeCount = (int) command.page().items().stream()
			.filter(item -> item.visible() && item.title() != null)
			.count();
		int localizationCount = (int) command.page().items().stream()
			.filter(item -> item.title() != null)
			.count();
		return new KtoStorePageResult(
			existing.callLogId(),
			existing.id(),
			existing.itemCount(),
			activeCount,
			localizationCount,
			true);
	}

	private void validateUniqueContentIds(List<KtoPlaceItem> items) {
		var contentIds = new HashSet<String>();
		for (KtoPlaceItem item : items) {
			if (!contentIds.add(item.contentId())) {
				throw new KtoDuplicateContentIdException();
			}
		}
	}

	private long insertCallLog(KtoStorePageCommand command) {
		String requestParams = json(requestParams(command));
		String responseSummary = json(responseSummary(command));
		var keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_call_logs
					(provider, api_name, operation, endpoint, request_started_at,
					 response_received_at, duration_ms, success, http_status,
					 request_params_masked, response_summary, external_result_code,
					 related_job_id, related_job_item_id, item_count, response_bytes)
				VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, '0000', ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, PROVIDER);
			statement.setString(2, API_NAME);
			statement.setString(3, OPERATION);
			statement.setString(4, ENDPOINT);
			statement.setTimestamp(5, Timestamp.from(command.call().requestedAt()));
			statement.setTimestamp(6, Timestamp.from(command.call().responseReceivedAt()));
			statement.setLong(7, command.call().durationMs());
			statement.setInt(8, command.call().httpStatus());
			statement.setString(9, requestParams);
			statement.setString(10, responseSummary);
			if (command.batchExecution() == null) {
				statement.setNull(11, java.sql.Types.BIGINT);
				statement.setNull(12, java.sql.Types.BIGINT);
			} else {
				statement.setLong(11, command.batchExecution().jobId());
				statement.setLong(12, command.batchExecution().jobItemId());
			}
			statement.setInt(13, command.page().items().size());
			statement.setLong(14, command.page().responseBytes());
			return statement;
		}, keyHolder);

		return requiredKey(keyHolder);
	}

	private Map<String, Object> requestParams(KtoStorePageCommand command) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("numOfRows", command.page().pageSize());
		params.put("pageNo", command.page().pageNumber());
		params.put("MobileOS", "ETC");
		params.put("MobileApp", "KoReady");
		params.put("_type", "json");
		params.put("serviceKey", "***");
		return params;
	}

	private Map<String, Object> responseSummary(KtoStorePageCommand command) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("resultCode", "0000");
		summary.put("pageNo", command.page().pageNumber());
		summary.put("numOfRows", command.page().pageSize());
		summary.put("totalCount", command.page().totalCount());
		summary.put("responseSha256", command.page().responseSha256());
		return summary;
	}

	private long insertSnapshot(KtoStorePageCommand command, long callLogId) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_raw_snapshots
					(call_log_id, provider, api_name, operation, storage_key,
					 storage_format, content_type, raw_content_sha256,
					 stored_object_sha256, byte_size, compressed_byte_size,
					 item_count, captured_at, retention_class, immutable)
				VALUES (?, 'KTO', 'KOR', 'areaBasedSyncList2', ?, 'JSON_GZIP',
				        'application/json', ?, ?, ?, ?, ?, ?, 'COMPETITION_EVIDENCE', TRUE)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, callLogId);
			statement.setString(2, command.snapshot().storageKey());
			statement.setString(3, command.page().responseSha256());
			statement.setString(4, command.snapshot().storedObjectSha256());
			statement.setLong(5, command.page().responseBytes());
			statement.setLong(6, command.snapshot().compressedByteSize());
			statement.setInt(7, command.page().items().size());
			statement.setTimestamp(8, Timestamp.from(command.snapshot().capturedAt()));
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private long requiredKey(GeneratedKeyHolder keyHolder) {
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Database did not return a generated key");
		}
		return key.longValue();
	}

	private Map<String, String> loadServiceRegions() {
		List<RegionRow> rows = jdbcTemplate.query(
			"""
			SELECT code, service_region_code
			FROM administrative_regions
			WHERE provider = 'KTO' AND level = 'SIDO' AND parent_code = ''
			""",
			(rs, rowNumber) -> new RegionRow(
				rs.getString("code"),
				rs.getString("service_region_code")));
		Map<String, String> regions = new HashMap<>();
		rows.forEach(row -> regions.put(row.code(), row.serviceRegionCode()));
		return Map.copyOf(regions);
	}

	private PlaceRow toPlaceRow(KtoPlaceItem item, String serviceRegionCode) {
		String title = item.title();
		return new PlaceRow(
			item.contentId(),
			item.contentTypeId(),
			serviceRegionCode,
			item.areaCode(),
			item.districtCode(),
			item.legalDongRegionCode(),
			item.legalDongDistrictCode(),
			item.classificationCode1(),
			item.classificationCode2(),
			item.classificationCode3(),
			joinAddress(item.address1(), item.address2()),
			coordinate(item.latitude(), -90, 90),
			coordinate(item.longitude(), -180, 180),
			item.phoneNumber(),
			item.primaryImageUrl(),
			modifiedTime(item.modifiedTime()),
			item.visible(),
			item.visible() && title != null,
			title,
			item.sourceHash());
	}

	private String joinAddress(String address1, String address2) {
		if (address1 == null) {
			return address2;
		}
		if (address2 == null) {
			return address1;
		}
		return address1 + " " + address2;
	}

	private BigDecimal coordinate(String value, int minimum, int maximum) {
		if (value == null) {
			return null;
		}
		try {
			BigDecimal coordinate = new BigDecimal(value);
			if (coordinate.compareTo(BigDecimal.valueOf(minimum)) < 0
				|| coordinate.compareTo(BigDecimal.valueOf(maximum)) > 0) {
				return null;
			}
			return coordinate;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private LocalDateTime modifiedTime(String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(value, KTO_TIMESTAMP);
		} catch (DateTimeParseException exception) {
			return null;
		}
	}

	private void upsertPlaces(List<PlaceRow> places) {
		if (places.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			UPSERT_PLACE_SQL,
			places,
			batchProperties.flushSize(),
			(statement, place) -> {
				statement.setString(1, place.contentId());
				statement.setString(2, place.contentTypeId());
				statement.setString(3, place.serviceRegionCode());
				statement.setString(4, place.areaCode());
				statement.setString(5, place.districtCode());
				statement.setString(6, place.legalDongRegionCode());
				statement.setString(7, place.legalDongDistrictCode());
				statement.setString(8, place.classificationCode1());
				statement.setString(9, place.classificationCode2());
				statement.setString(10, place.classificationCode3());
				statement.setString(11, place.address());
				statement.setBigDecimal(12, place.latitude());
				statement.setBigDecimal(13, place.longitude());
				statement.setString(14, place.phoneNumber());
				statement.setString(15, place.primaryImageUrl());
				statement.setObject(16, place.sourceModifiedTime());
				statement.setBoolean(17, place.showFlag());
				statement.setBoolean(18, place.active());
			});
	}

	private Map<String, Long> loadPlaceIds(List<PlaceRow> places) {
		if (places.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", Collections.nCopies(places.size(), "?"));
		Object[] contentIds = places.stream().map(PlaceRow::contentId).toArray();
		List<IdRow> rows = jdbcTemplate.query(
			"SELECT id, kto_content_id FROM places WHERE kto_content_id IN (" + placeholders + ")",
			(rs, rowNumber) -> new IdRow(rs.getString("kto_content_id"), rs.getLong("id")),
			contentIds);
		Map<String, Long> ids = new HashMap<>();
		rows.forEach(row -> ids.put(row.contentId(), row.id()));
		if (ids.size() != places.size()) {
			throw new IllegalStateException("Not all KTO places were persisted");
		}
		return Map.copyOf(ids);
	}

	private void upsertLocalizations(List<PlaceRow> places, Map<String, Long> placeIds) {
		List<PlaceRow> localized = places.stream().filter(place -> place.title() != null).toList();
		if (localized.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			UPSERT_LOCALIZATION_SQL,
			localized,
			batchProperties.flushSize(),
			(statement, place) -> {
				statement.setLong(1, placeIds.get(place.contentId()));
				statement.setString(2, place.title());
				statement.setString(3, place.address());
				statement.setString(4, place.contentId());
				statement.setString(5, place.sourceHash());
			});
	}

	private void insertSourceRecords(
		KtoStorePageCommand command,
		long snapshotId,
		List<PlaceRow> places
	) {
		if (places.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			INSERT_SOURCE_RECORD_SQL,
			places,
			batchProperties.flushSize(),
			(statement, place) -> {
				statement.setString(1, place.contentId());
				statement.setLong(2, snapshotId);
				statement.setObject(3, place.sourceModifiedTime());
				statement.setString(4, place.sourceHash());
				statement.setTimestamp(5, Timestamp.from(command.snapshot().capturedAt()));
			});
	}

	private Map<String, Long> loadSourceRecordIds(long snapshotId) {
		List<IdRow> rows = jdbcTemplate.query(
			"""
			SELECT id, source_content_id
			FROM place_source_records
			WHERE raw_snapshot_id = ? AND language = 'KO'
			""",
			(rs, rowNumber) -> new IdRow(
				rs.getString("source_content_id"),
				rs.getLong("id")),
			snapshotId);
		Map<String, Long> ids = new HashMap<>();
		rows.forEach(row -> ids.put(row.contentId(), row.id()));
		return Map.copyOf(ids);
	}

	private void insertSourceMatches(
		List<PlaceRow> places,
		Map<String, Long> placeIds,
		Map<String, Long> sourceRecordIds
	) {
		if (places.isEmpty()) {
			return;
		}
		String evidence = json(Map.of("contentIdExact", true, "source", "KTO_KO"));
		jdbcTemplate.batchUpdate(
			INSERT_SOURCE_MATCH_SQL,
			places,
			batchProperties.flushSize(),
			(statement, place) -> {
				statement.setLong(1, sourceRecordIds.get(place.contentId()));
				statement.setLong(2, placeIds.get(place.contentId()));
				statement.setString(3, evidence);
				statement.setString(4, MATCHER_VERSION);
			});
	}

	private void advancePageCursor(KtoStorePageCommand command) {
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
				(provider, api_name, operation, cursor_type, cursor_value,
				 last_success_at, failure_count, enabled)
			VALUES ('KTO', 'KOR', 'areaBasedSyncList2', 'PAGE', ?, ?, 0, TRUE)
			ON DUPLICATE KEY UPDATE
				cursor_value = IF(
					cursor_value IS NULL,
					VALUES(cursor_value),
					GREATEST(
						CAST(cursor_value AS UNSIGNED),
						CAST(VALUES(cursor_value) AS UNSIGNED)
					)
				),
				last_success_at = VALUES(last_success_at),
				last_failure_at = NULL,
				failure_count = 0,
				enabled = TRUE
			""",
			Integer.toString(command.page().pageNumber()),
			Timestamp.from(command.snapshot().capturedAt()));
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("KTO storage metadata could not be serialized");
		}
	}

	private record ExistingSnapshot(
		long id,
		long callLogId,
		String rawContentSha256,
		String storedObjectSha256,
		long byteSize,
		long compressedByteSize,
		int itemCount
	) {
	}

	private record IdRow(String contentId, long id) {
	}

	private record RegionRow(String code, String serviceRegionCode) {
	}

	private record PlaceRow(
		String contentId,
		String contentTypeId,
		String serviceRegionCode,
		String areaCode,
		String districtCode,
		String legalDongRegionCode,
		String legalDongDistrictCode,
		String classificationCode1,
		String classificationCode2,
		String classificationCode3,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		String phoneNumber,
		String primaryImageUrl,
		LocalDateTime sourceModifiedTime,
		boolean showFlag,
		boolean active,
		String title,
		String sourceHash
	) {
	}
}
