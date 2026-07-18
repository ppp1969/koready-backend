package koready_backend.kto.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
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
import koready_backend.kto.application.model.KtoFestivalStorePageResult;
import koready_backend.kto.application.model.KtoStoreFestivalPageCommand;
import koready_backend.kto.application.port.KtoFestivalPageStore;
import koready_backend.kto.domain.KtoFestivalItem;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class KtoFestivalPageJdbcStore implements KtoFestivalPageStore {

	private static final String PROVIDER = "KTO";
	private static final String API_NAME = "KOR";
	private static final String OPERATION = "searchFestival2";
	private static final String ENDPOINT =
		"https://apis.data.go.kr/B551011/KorService2/searchFestival2";
	private static final String MATCHER_VERSION = "kto-ko-content-id-v1";
	private static final DateTimeFormatter KTO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final DateTimeFormatter KTO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private static final String UPSERT_PLACE_SQL = """
		INSERT INTO places
			(kto_content_id, kto_content_type_id, service_region_code,
			 area_code, sigungu_code, ldong_regn_cd, ldong_signgu_cd,
			 lcls_systm1, lcls_systm2, lcls_systm3, address,
			 latitude, longitude, tel, first_image_url, source_modified_time,
			 show_flag, active)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, FALSE)
		ON DUPLICATE KEY UPDATE
			kto_content_type_id = VALUES(kto_content_type_id),
			service_region_code = COALESCE(VALUES(service_region_code), service_region_code),
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
			show_flag = TRUE
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
		VALUES ('KTO', 'KOR', 'searchFestival2', ?, 'KO', ?, ?, ?, ?)
		""";

	private static final String INSERT_SOURCE_MATCH_SQL = """
		INSERT INTO place_source_matches
			(source_record_id, place_id, match_method, confidence,
			 candidate_count, evidence_json, status, matcher_version)
		VALUES (?, ?, 'CONTENT_ID', 1.0000, 1, ?, 'AUTO_CONFIRMED', ?)
		""";

	private static final String UPSERT_SERIES_SQL = """
		INSERT INTO festival_series
			(series_key, canonical_place_id, title_ko, match_status)
		VALUES (?, ?, ?, 'AUTO_CONFIRMED')
		ON DUPLICATE KEY UPDATE
			canonical_place_id = VALUES(canonical_place_id),
			title_ko = VALUES(title_ko)
		""";

	private static final String UPSERT_OCCURRENCE_SQL = """
		INSERT INTO place_event_occurrences
			(festival_series_id, place_id, event_year, occurrence_sequence,
			 start_date, end_date, provider, source_content_id, source_operation,
			 source_hash, visible_from, date_validation_status)
		VALUES (?, ?, ?, 1, ?, ?, 'KTO', ?, 'searchFestival2', ?, ?, 'VALID')
		ON DUPLICATE KEY UPDATE
			festival_series_id = VALUES(festival_series_id),
			place_id = VALUES(place_id),
			start_date = VALUES(start_date),
			end_date = VALUES(end_date),
			source_operation = 'searchFestival2',
			source_hash = VALUES(source_hash),
			visible_from = VALUES(visible_from),
			date_validation_status = 'VALID'
		""";

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;
	private final KtoBatchProperties batchProperties;

	public KtoFestivalPageJdbcStore(
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
	public KtoFestivalStorePageResult store(KtoStoreFestivalPageCommand command) {
		Objects.requireNonNull(command, "KTO festival page storage command is required");
		ExistingSnapshot existing = findExistingSnapshot(command.snapshot().storageKey());
		if (existing != null) {
			return replayExisting(command, existing);
		}

		validateUniqueContentIds(command.page().items());
		long callLogId = insertCallLog(command);
		long snapshotId = insertSnapshot(command, callLogId);
		RegionMappings regionMappings = loadRegionMappings();
		List<FestivalRow> festivals = command.page().items().stream()
			.map(item -> toFestivalRow(item, regionMappings))
			.toList();

		upsertPlaces(festivals);
		Map<String, Long> placeIds = loadPlaceIds(festivals);
		upsertLocalizations(festivals, placeIds);
		insertSourceRecords(command, snapshotId, festivals);
		Map<String, Long> sourceRecordIds = loadSourceRecordIds(snapshotId);
		insertSourceMatches(festivals, placeIds, sourceRecordIds);
		upsertSeries(festivals, placeIds);
		Map<String, Long> seriesIds = loadSeriesIds(festivals);
		upsertOccurrences(festivals, placeIds, seriesIds);
		advanceDateCursor(command);

		return new KtoFestivalStorePageResult(
			callLogId,
			snapshotId,
			festivals.size(),
			festivals.size(),
			false);
	}

	private ExistingSnapshot findExistingSnapshot(String storageKey) {
		List<ExistingSnapshot> rows = jdbcTemplate.query(
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
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private KtoFestivalStorePageResult replayExisting(
		KtoStoreFestivalPageCommand command,
		ExistingSnapshot existing
	) {
		if (!existing.rawContentSha256().equals(command.page().responseSha256())
			|| !existing.storedObjectSha256().equals(command.snapshot().storedObjectSha256())
			|| existing.byteSize() != command.page().responseBytes()
			|| existing.compressedByteSize() != command.snapshot().compressedByteSize()
			|| existing.itemCount() != command.page().items().size()) {
			throw new KtoSnapshotConflictException();
		}
		return new KtoFestivalStorePageResult(
			existing.callLogId(),
			existing.id(),
			existing.itemCount(),
			existing.itemCount(),
			true);
	}

	private void validateUniqueContentIds(List<KtoFestivalItem> items) {
		var ids = new HashSet<String>();
		for (KtoFestivalItem item : items) {
			if (!ids.add(item.place().contentId())) {
				throw new KtoDuplicateContentIdException();
			}
		}
	}

	private long insertCallLog(KtoStoreFestivalPageCommand command) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_call_logs
					(provider, api_name, operation, endpoint, request_started_at,
					 response_received_at, duration_ms, success, http_status,
					 request_params_masked, response_summary, external_result_code,
					 item_count, response_bytes)
				VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, '0000', ?, ?)
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
			statement.setString(9, json(requestParams(command)));
			statement.setString(10, json(responseSummary(command)));
			statement.setInt(11, command.page().items().size());
			statement.setLong(12, command.page().responseBytes());
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private Map<String, Object> requestParams(KtoStoreFestivalPageCommand command) {
		Map<String, Object> params = new LinkedHashMap<>();
		params.put("numOfRows", command.page().pageSize());
		params.put("pageNo", command.page().pageNumber());
		params.put("MobileOS", "ETC");
		params.put("MobileApp", "KoReady");
		params.put("_type", "json");
		params.put("eventStartDate", KTO_DATE.format(command.eventStartDate()));
		params.put("serviceKey", "***");
		return params;
	}

	private Map<String, Object> responseSummary(KtoStoreFestivalPageCommand command) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("resultCode", "0000");
		summary.put("pageNo", command.page().pageNumber());
		summary.put("numOfRows", command.page().pageSize());
		summary.put("totalCount", command.page().totalCount());
		summary.put("responseSha256", command.page().responseSha256());
		return summary;
	}

	private long insertSnapshot(KtoStoreFestivalPageCommand command, long callLogId) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_raw_snapshots
					(call_log_id, provider, api_name, operation, storage_key,
					 storage_format, content_type, raw_content_sha256,
					 stored_object_sha256, byte_size, compressed_byte_size,
					 item_count, captured_at, retention_class, immutable)
				VALUES (?, 'KTO', 'KOR', 'searchFestival2', ?, 'JSON_GZIP',
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

	private RegionMappings loadRegionMappings() {
		List<RegionRow> rows = jdbcTemplate.query(
			"""
			SELECT provider, code, service_region_code
			FROM administrative_regions
			WHERE provider IN ('KTO', 'KTO_LDONG')
			  AND level = 'SIDO' AND parent_code = ''
			""",
			(rs, rowNumber) -> new RegionRow(
				rs.getString("provider"),
				rs.getString("code"),
				rs.getString("service_region_code")));
		Map<String, String> areaCodes = new HashMap<>();
		Map<String, String> legalCodes = new HashMap<>();
		for (RegionRow row : rows) {
			("KTO".equals(row.provider()) ? areaCodes : legalCodes)
				.put(row.code(), row.serviceRegionCode());
		}
		return new RegionMappings(Map.copyOf(areaCodes), Map.copyOf(legalCodes));
	}

	private FestivalRow toFestivalRow(KtoFestivalItem festival, RegionMappings mappings) {
		KtoPlaceItem item = festival.place();
		String serviceRegion = item.areaCode() == null
			? null
			: mappings.areaCodes().get(item.areaCode());
		if (serviceRegion == null && item.legalDongRegionCode() != null) {
			serviceRegion = legalServiceRegion(
				mappings.legalCodes(),
				item.legalDongRegionCode());
		}
		return new FestivalRow(
			item.contentId(),
			item.contentTypeId(),
			serviceRegion,
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
			item.title(),
			item.sourceHash(),
			festival.startDate(),
			festival.endDate(),
			festival.visibleFrom(),
			festival.eventYear());
	}

	private String legalServiceRegion(Map<String, String> mappings, String legalRegionCode) {
		String exact = mappings.get(legalRegionCode);
		if (exact != null || legalRegionCode.length() <= 2) {
			return exact;
		}
		return mappings.get(legalRegionCode.substring(0, 2));
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

	private void upsertPlaces(List<FestivalRow> festivals) {
		if (festivals.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			UPSERT_PLACE_SQL,
			festivals,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setString(1, row.contentId());
				statement.setString(2, row.contentTypeId());
				statement.setString(3, row.serviceRegionCode());
				statement.setString(4, row.areaCode());
				statement.setString(5, row.districtCode());
				statement.setString(6, row.legalRegionCode());
				statement.setString(7, row.legalDistrictCode());
				statement.setString(8, row.classificationCode1());
				statement.setString(9, row.classificationCode2());
				statement.setString(10, row.classificationCode3());
				statement.setString(11, row.address());
				statement.setBigDecimal(12, row.latitude());
				statement.setBigDecimal(13, row.longitude());
				statement.setString(14, row.phoneNumber());
				statement.setString(15, row.primaryImageUrl());
				statement.setObject(16, row.sourceModifiedTime());
			});
	}

	private Map<String, Long> loadPlaceIds(List<FestivalRow> festivals) {
		return loadIds(
			festivals.stream().map(FestivalRow::contentId).toList(),
			"SELECT id, kto_content_id AS external_id FROM places WHERE kto_content_id IN (%s)");
	}

	private void upsertLocalizations(List<FestivalRow> festivals, Map<String, Long> placeIds) {
		if (festivals.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			UPSERT_LOCALIZATION_SQL,
			festivals,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setLong(1, placeIds.get(row.contentId()));
				statement.setString(2, row.title());
				statement.setString(3, row.address());
				statement.setString(4, row.contentId());
				statement.setString(5, row.sourceHash());
			});
	}

	private void insertSourceRecords(
		KtoStoreFestivalPageCommand command,
		long snapshotId,
		List<FestivalRow> festivals
	) {
		if (festivals.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			INSERT_SOURCE_RECORD_SQL,
			festivals,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setString(1, row.contentId());
				statement.setLong(2, snapshotId);
				statement.setObject(3, row.sourceModifiedTime());
				statement.setString(4, row.sourceHash());
				statement.setTimestamp(5, Timestamp.from(command.snapshot().capturedAt()));
			});
	}

	private Map<String, Long> loadSourceRecordIds(long snapshotId) {
		List<IdRow> rows = jdbcTemplate.query(
			"""
			SELECT id, source_content_id AS external_id
			FROM place_source_records
			WHERE raw_snapshot_id = ? AND language = 'KO'
			""",
			(rs, rowNumber) -> new IdRow(rs.getString("external_id"), rs.getLong("id")),
			snapshotId);
		return idMap(rows);
	}

	private void insertSourceMatches(
		List<FestivalRow> festivals,
		Map<String, Long> placeIds,
		Map<String, Long> sourceRecordIds
	) {
		if (festivals.isEmpty()) {
			return;
		}
		String evidence = json(Map.of("contentIdExact", true, "source", "KTO_KO"));
		jdbcTemplate.batchUpdate(
			INSERT_SOURCE_MATCH_SQL,
			festivals,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setLong(1, sourceRecordIds.get(row.contentId()));
				statement.setLong(2, placeIds.get(row.contentId()));
				statement.setString(3, evidence);
				statement.setString(4, MATCHER_VERSION);
			});
	}

	private void upsertSeries(List<FestivalRow> festivals, Map<String, Long> placeIds) {
		if (festivals.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			UPSERT_SERIES_SQL,
			festivals,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setString(1, seriesKey(row.contentId()));
				statement.setLong(2, placeIds.get(row.contentId()));
				statement.setString(3, row.title());
			});
	}

	private Map<String, Long> loadSeriesIds(List<FestivalRow> festivals) {
		return loadIds(
			festivals.stream().map(row -> seriesKey(row.contentId())).toList(),
			"SELECT id, series_key AS external_id FROM festival_series WHERE series_key IN (%s)");
	}

	private void upsertOccurrences(
		List<FestivalRow> festivals,
		Map<String, Long> placeIds,
		Map<String, Long> seriesIds
	) {
		if (festivals.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			UPSERT_OCCURRENCE_SQL,
			festivals,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setLong(1, seriesIds.get(seriesKey(row.contentId())));
				statement.setLong(2, placeIds.get(row.contentId()));
				statement.setInt(3, row.eventYear());
				statement.setObject(4, row.startDate());
				statement.setObject(5, row.endDate());
				statement.setString(6, row.contentId());
				statement.setString(7, row.sourceHash());
				statement.setObject(8, row.visibleFrom());
			});
	}

	private Map<String, Long> loadIds(List<String> externalIds, String sqlTemplate) {
		if (externalIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = String.join(",", Collections.nCopies(externalIds.size(), "?"));
		List<IdRow> rows = jdbcTemplate.query(
			sqlTemplate.formatted(placeholders),
			(rs, rowNumber) -> new IdRow(rs.getString("external_id"), rs.getLong("id")),
			externalIds.toArray());
		Map<String, Long> ids = idMap(rows);
		if (ids.size() != externalIds.size()) {
			throw new IllegalStateException("Not all KTO festival records were persisted");
		}
		return ids;
	}

	private Map<String, Long> idMap(List<IdRow> rows) {
		Map<String, Long> ids = new HashMap<>();
		rows.forEach(row -> ids.put(row.externalId(), row.id()));
		return Map.copyOf(ids);
	}

	private String seriesKey(String contentId) {
		return "KTO:" + contentId;
	}

	private void advanceDateCursor(KtoStoreFestivalPageCommand command) {
		String cursor = KTO_DATE.format(command.eventStartDate()) + ":" + command.page().pageNumber();
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
				(provider, api_name, operation, cursor_type, cursor_value,
				 last_success_at, failure_count, enabled)
			VALUES ('KTO', 'KOR', 'searchFestival2', 'DATE_RANGE', ?, ?, 0, TRUE)
			ON DUPLICATE KEY UPDATE
				cursor_value = VALUES(cursor_value),
				last_success_at = VALUES(last_success_at),
				last_failure_at = NULL,
				failure_count = 0,
				enabled = TRUE
			""",
			cursor,
			Timestamp.from(command.snapshot().capturedAt()));
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("KTO festival metadata could not be serialized");
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

	private record IdRow(String externalId, long id) {
	}

	private record RegionRow(String provider, String code, String serviceRegionCode) {
	}

	private record RegionMappings(
		Map<String, String> areaCodes,
		Map<String, String> legalCodes
	) {
	}

	private record FestivalRow(
		String contentId,
		String contentTypeId,
		String serviceRegionCode,
		String areaCode,
		String districtCode,
		String legalRegionCode,
		String legalDistrictCode,
		String classificationCode1,
		String classificationCode2,
		String classificationCode3,
		String address,
		BigDecimal latitude,
		BigDecimal longitude,
		String phoneNumber,
		String primaryImageUrl,
		LocalDateTime sourceModifiedTime,
		String title,
		String sourceHash,
		LocalDate startDate,
		LocalDate endDate,
		LocalDate visibleFrom,
		int eventYear
	) {
	}
}
