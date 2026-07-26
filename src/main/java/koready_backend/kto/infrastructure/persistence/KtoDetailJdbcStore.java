package koready_backend.kto.infrastructure.persistence;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.model.KtoStoreDetailCommand;
import koready_backend.kto.application.model.KtoStoredDetailOperation;
import koready_backend.kto.application.port.KtoDetailStore;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailTarget;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class KtoDetailJdbcStore implements KtoDetailStore {

	private static final String ENDPOINT_PREFIX =
		"https://apis.data.go.kr/B551011/KorService2/";
	private static final Set<String> IDENTITY_FIELDS = Set.of(
		"contentid", "contenttypeid", "serialnum");

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;

	public KtoDetailJdbcStore(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
	}

	@Override
	@Transactional
	public void store(KtoStoreDetailCommand command) {
		Map<KtoDetailOperation, ExistingSnapshot> existing = existing(command);
		if (!existing.isEmpty()) {
			if (existing.size() != KtoDetailOperation.values().length) {
				throw new KtoSnapshotConflictException();
			}
			verifyReplay(command, existing);
			return;
		}

		Map<KtoDetailOperation, Long> snapshotIds = new LinkedHashMap<>();
		for (KtoStoredDetailOperation operation : command.operations()) {
			validateContentIds(command.target(), operation);
			long callLogId = insertCallLog(command, operation);
			long snapshotId = insertSnapshot(operation, callLogId);
			snapshotIds.put(operation.fetched().response().operation(), snapshotId);
		}

		updateCommon(command.target(), operation(command, KtoDetailOperation.COMMON));
		replaceAttributes(
			command.target(),
			operation(command, KtoDetailOperation.INTRO),
			snapshotIds.get(KtoDetailOperation.INTRO));
		replaceAttributes(
			command.target(),
			operation(command, KtoDetailOperation.INFO),
			snapshotIds.get(KtoDetailOperation.INFO));
		replaceImages(
			command.target(),
			operation(command, KtoDetailOperation.IMAGE));
		advanceCursors(command.target().placeId(), command.operations());
	}

	private Map<KtoDetailOperation, ExistingSnapshot> existing(
		KtoStoreDetailCommand command
	) {
		Map<KtoDetailOperation, ExistingSnapshot> existing = new LinkedHashMap<>();
		for (KtoStoredDetailOperation operation : command.operations()) {
			jdbcTemplate.query(
				"""
				SELECT id, raw_content_sha256, stored_object_sha256,
				       byte_size, compressed_byte_size, item_count
				FROM open_api_raw_snapshots
				WHERE storage_key = ?
				""",
				(resultSet, rowNumber) -> {
					existing.put(
						operation.fetched().response().operation(),
						new ExistingSnapshot(
							resultSet.getLong("id"),
							resultSet.getString("raw_content_sha256"),
							resultSet.getString("stored_object_sha256"),
							resultSet.getLong("byte_size"),
							resultSet.getLong("compressed_byte_size"),
							resultSet.getInt("item_count")));
					return 0;
				},
				operation.snapshot().storageKey());
		}
		return Map.copyOf(existing);
	}

	private void verifyReplay(
		KtoStoreDetailCommand command,
		Map<KtoDetailOperation, ExistingSnapshot> existing
	) {
		for (KtoStoredDetailOperation operation : command.operations()) {
			ExistingSnapshot stored = existing.get(
				operation.fetched().response().operation());
			if (stored == null
				|| !stored.rawHash().equals(
					operation.fetched().response().responseSha256())
				|| !stored.objectHash().equals(
					operation.snapshot().storedObjectSha256())
				|| stored.byteSize()
					!= operation.fetched().response().responseBytes()
				|| stored.compressedByteSize()
					!= operation.snapshot().compressedByteSize()
				|| stored.itemCount()
					!= operation.fetched().response().items().size()) {
				throw new KtoSnapshotConflictException();
			}
		}
	}

	private void validateContentIds(
		KtoDetailTarget target,
		KtoStoredDetailOperation operation
	) {
		for (Map<String, String> item : operation.fetched().response().items()) {
			String contentId = item.get("contentid");
			if (contentId != null && !target.contentId().equals(contentId)) {
				throw new IllegalStateException(
					"KTO detail content ID did not match its target");
			}
			String contentTypeId = item.get("contenttypeid");
			if (contentTypeId != null
				&& !target.contentTypeId().equals(contentTypeId)) {
				throw new IllegalStateException(
					"KTO detail content type did not match its target");
			}
		}
	}

	private long insertCallLog(
		KtoStoreDetailCommand command,
		KtoStoredDetailOperation operation
	) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_call_logs
					(provider, api_name, operation, endpoint, request_started_at,
					 response_received_at, duration_ms, success, http_status,
					 request_params_masked, response_summary, external_result_code,
					 related_job_id, related_job_item_id, item_count, response_bytes)
				VALUES ('KTO', 'KOR', ?, ?, ?, ?, ?, TRUE, ?,
				        CAST(? AS JSON), CAST(? AS JSON), '0000', ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			var response = operation.fetched().response();
			statement.setString(1, response.operation().apiName());
			statement.setString(2, ENDPOINT_PREFIX + response.operation().apiName());
			statement.setTimestamp(
				3, Timestamp.from(operation.fetched().call().requestedAt()));
			statement.setTimestamp(
				4, Timestamp.from(operation.fetched().call().responseReceivedAt()));
			statement.setLong(5, operation.fetched().call().durationMs());
			statement.setInt(6, operation.fetched().call().httpStatus());
			statement.setString(7, json(Map.of(
				"contentId", command.target().contentId(),
				"contentTypeId", command.target().contentTypeId(),
				"serviceKey", "***")));
			statement.setString(8, json(Map.of(
				"itemCount", response.items().size(),
				"responseSha256", response.responseSha256())));
			if (command.batchExecution() == null) {
				statement.setNull(9, java.sql.Types.BIGINT);
				statement.setNull(10, java.sql.Types.BIGINT);
			} else {
				statement.setLong(9, command.batchExecution().jobId());
				statement.setLong(10, command.batchExecution().jobItemId());
			}
			statement.setInt(11, response.items().size());
			statement.setLong(12, response.responseBytes());
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private long insertSnapshot(
		KtoStoredDetailOperation operation,
		long callLogId
	) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_raw_snapshots
					(call_log_id, provider, api_name, operation, storage_key,
					 storage_format, content_type, raw_content_sha256,
					 stored_object_sha256, byte_size, compressed_byte_size,
					 item_count, captured_at, retention_class, immutable)
				VALUES (?, 'KTO', 'KOR', ?, ?, 'JSON_GZIP',
				        'application/json', ?, ?, ?, ?, ?, ?,
				        'COMPETITION_EVIDENCE', TRUE)
				""",
				Statement.RETURN_GENERATED_KEYS);
			var response = operation.fetched().response();
			statement.setLong(1, callLogId);
			statement.setString(2, response.operation().apiName());
			statement.setString(3, operation.snapshot().storageKey());
			statement.setString(4, response.responseSha256());
			statement.setString(5, operation.snapshot().storedObjectSha256());
			statement.setLong(6, response.responseBytes());
			statement.setLong(7, operation.snapshot().compressedByteSize());
			statement.setInt(8, response.items().size());
			statement.setTimestamp(9, Timestamp.from(operation.snapshot().capturedAt()));
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private void updateCommon(
		KtoDetailTarget target,
		KtoStoredDetailOperation operation
	) {
		if (operation.fetched().response().items().isEmpty()) {
			return;
		}
		Map<String, String> item = operation.fetched().response().items().getFirst();
		String address = join(item.get("addr1"), item.get("addr2"));
		jdbcTemplate.update(
			"""
			UPDATE places
			SET address = COALESCE(?, address),
			    latitude = COALESCE(?, latitude),
			    longitude = COALESCE(?, longitude),
			    tel = COALESCE(?, tel),
			    homepage = COALESCE(?, homepage),
			    first_image_url = COALESCE(?, first_image_url)
			WHERE id = ? AND kto_content_id = ?
			""",
			optional(address, 500),
			decimal(item.get("mapy"), -90, 90),
			decimal(item.get("mapx"), -180, 180),
			optional(item.get("tel"), 255),
			optional(item.get("homepage"), 1000),
			httpUrl(item.get("firstimage"), 1000),
			target.placeId(),
			target.contentId());
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
				(place_id, language, title, overview, address_text,
				 translation_source, source_content_id, source_hash)
			VALUES (?, 'KO', ?, ?, ?, 'KTO_KO', ?, ?)
			ON DUPLICATE KEY UPDATE
				title = IF(translation_source = 'MANUAL_EDITED',
					title, COALESCE(VALUES(title), title)),
				overview = IF(translation_source = 'MANUAL_EDITED',
					overview, COALESCE(VALUES(overview), overview)),
				address_text = IF(translation_source = 'MANUAL_EDITED',
					address_text, COALESCE(VALUES(address_text), address_text)),
				translation_source = IF(
					translation_source = 'MANUAL_EDITED',
					translation_source, 'KTO_KO'),
				source_content_id = IF(
					translation_source = 'MANUAL_EDITED',
					source_content_id, VALUES(source_content_id)),
				source_hash = IF(
					translation_source = 'MANUAL_EDITED',
					source_hash, VALUES(source_hash))
			""",
			target.placeId(),
			optional(item.get("title"), 300),
			optionalText(item.get("overview")),
			optional(address, 500),
			target.contentId(),
			operation.fetched().response().responseSha256());
	}

	private void replaceAttributes(
		KtoDetailTarget target,
		KtoStoredDetailOperation operation,
		long snapshotId
	) {
		String apiName = operation.fetched().response().operation().apiName();
		jdbcTemplate.update(
			"DELETE FROM place_detail_attributes WHERE place_id = ? AND source_operation = ?",
			target.placeId(),
			apiName);
		var rows = new ArrayList<AttributeRow>();
		int fallbackSequence = 0;
		for (Map<String, String> item : operation.fetched().response().items()) {
			fallbackSequence++;
			int sequence = positiveInteger(item.get("serialnum"), fallbackSequence);
			for (Map.Entry<String, String> field : item.entrySet()) {
				if (IDENTITY_FIELDS.contains(field.getKey())
					|| field.getValue() == null
					|| field.getValue().isBlank()) {
					continue;
				}
				rows.add(new AttributeRow(
					target.placeId(),
					apiName,
					sequence,
					field.getKey(),
					field.getValue(),
					target.contentId(),
					snapshotId,
					operation.fetched().response().responseSha256()));
			}
			if (operation.fetched().response().operation() == KtoDetailOperation.INFO
				&& feeName(item.get("infoname"))
				&& item.get("infotext") != null
				&& !item.get("infotext").isBlank()) {
				rows.add(new AttributeRow(
					target.placeId(),
					apiName,
					sequence,
					"normalized_usagefee",
					item.get("infotext"),
					target.contentId(),
					snapshotId,
					operation.fetched().response().responseSha256()));
			}
		}
		if (rows.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO place_detail_attributes
				(place_id, source_operation, item_sequence, field_code,
				 value_text, source_content_id, source_snapshot_id, source_hash)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			rows,
			50,
			(statement, row) -> {
				statement.setLong(1, row.placeId());
				statement.setString(2, row.operation());
				statement.setInt(3, row.sequence());
				statement.setString(4, row.fieldCode());
				statement.setString(5, row.value());
				statement.setString(6, row.contentId());
				statement.setLong(7, row.snapshotId());
				statement.setString(8, row.sourceHash());
			});
	}

	private void replaceImages(
		KtoDetailTarget target,
		KtoStoredDetailOperation operation
	) {
		jdbcTemplate.update(
			"DELETE FROM place_images WHERE place_id = ? AND source_type = 'KTO_DETAIL'",
			target.placeId());
		var rows = new ArrayList<ImageRow>();
		Set<String> seen = new HashSet<>();
		int fallbackOrder = 0;
		for (Map<String, String> item : operation.fetched().response().items()) {
			fallbackOrder++;
			String imageUrl = httpUrl(item.get("originimgurl"), 1000);
			if (imageUrl == null || !seen.add(imageUrl)) {
				continue;
			}
			rows.add(new ImageRow(
				imageUrl,
				httpUrl(item.get("smallimageurl"), 1000),
				positiveInteger(item.get("serialnum"), fallbackOrder),
				optional(item.get("imgname"), 500),
				optional(item.get("cpyrhtDivCd"), 30)));
		}
		if (rows.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO place_images
				(place_id, image_url, thumbnail_image_url, image_url_sha256,
				 source_type, source_priority, source_order, source_content_id,
				 source_image_name, copyright_type)
			VALUES (?, ?, ?, ?, 'KTO_DETAIL', 100, ?, ?, ?, ?)
			""",
			rows,
			50,
			(statement, row) -> {
				statement.setLong(1, target.placeId());
				statement.setString(2, row.imageUrl());
				statement.setString(3, row.thumbnailUrl());
				statement.setString(4, sha256(row.imageUrl()));
				statement.setInt(5, row.order());
				statement.setString(6, target.contentId());
				statement.setString(7, row.imageName());
				statement.setString(8, row.copyrightType());
			});
	}

	private void advanceCursors(
		long placeId,
		List<KtoStoredDetailOperation> operations
	) {
		Instant lastSuccessAt = operations.stream()
			.map(operation -> operation.snapshot().capturedAt())
			.max(Instant::compareTo)
			.orElseThrow();
		for (KtoStoredDetailOperation operation : operations) {
			jdbcTemplate.update(
				"""
				INSERT INTO tour_api_sync_cursors
					(provider, api_name, operation, cursor_type, cursor_value,
					 last_success_at, failure_count, enabled)
				VALUES ('KTO', 'KOR', ?, 'MANUAL', ?, ?, 0, TRUE)
				ON DUPLICATE KEY UPDATE
					cursor_value = IF(
						cursor_value IS NULL,
						VALUES(cursor_value),
						GREATEST(
							CAST(cursor_value AS UNSIGNED),
							CAST(VALUES(cursor_value) AS UNSIGNED))),
					last_success_at = VALUES(last_success_at),
					last_failure_at = NULL,
					failure_count = 0,
					enabled = TRUE
				""",
				operation.fetched().response().operation().apiName(),
				Long.toString(placeId),
				Timestamp.from(lastSuccessAt));
		}
	}

	private KtoStoredDetailOperation operation(
		KtoStoreDetailCommand command,
		KtoDetailOperation expected
	) {
		return command.operations().stream()
			.filter(operation -> operation.fetched().response().operation() == expected)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"Missing KTO detail operation: " + expected));
	}

	private BigDecimal decimal(String value, int minimum, int maximum) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			BigDecimal parsed = new BigDecimal(value.strip());
			if (parsed.compareTo(BigDecimal.valueOf(minimum)) < 0
				|| parsed.compareTo(BigDecimal.valueOf(maximum)) > 0) {
				throw new IllegalStateException("KTO detail coordinate is outside its range");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("KTO detail coordinate is invalid");
		}
	}

	private String join(String first, String second) {
		if (first == null || first.isBlank()) {
			return second;
		}
		if (second == null || second.isBlank()) {
			return first;
		}
		return first.strip() + " " + second.strip();
	}

	private String optional(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalStateException("KTO detail field is too long");
		}
		return normalized;
	}

	private String optionalText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}

	private String httpUrl(String value, int maxLength) {
		String normalized = optional(value, maxLength);
		if (normalized == null) {
			return null;
		}
		try {
			URI uri = new URI(normalized);
			String scheme = uri.getScheme();
			if (scheme == null
				|| !(scheme.equalsIgnoreCase("http")
					|| scheme.equalsIgnoreCase("https"))
				|| uri.getHost() == null) {
				throw new IllegalStateException("KTO detail image URL is invalid");
			}
			return normalized;
		} catch (URISyntaxException exception) {
			throw new IllegalStateException("KTO detail image URL is invalid");
		}
	}

	private int positiveInteger(String value, int fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			int parsed = Integer.parseInt(value);
			return parsed < 1 ? fallback : parsed;
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private boolean feeName(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		String normalized = value.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("요금")
			|| normalized.contains("입장")
			|| normalized.contains("이용료")
			|| normalized.contains("admission")
			|| normalized.contains("fee");
	}

	private long requiredKey(GeneratedKeyHolder keyHolder) {
		Number key = keyHolder.getKey();
		if (key == null || key.longValue() <= 0) {
			throw new IllegalStateException("Database did not return a generated key");
		}
		return key.longValue();
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException(
				"KTO detail metadata could not be serialized");
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private record ExistingSnapshot(
		long id,
		String rawHash,
		String objectHash,
		long byteSize,
		long compressedByteSize,
		int itemCount
	) {
	}

	private record AttributeRow(
		long placeId,
		String operation,
		int sequence,
		String fieldCode,
		String value,
		String contentId,
		long snapshotId,
		String sourceHash
	) {
	}

	private record ImageRow(
		String imageUrl,
		String thumbnailUrl,
		int order,
		String imageName,
		String copyrightType
	) {
	}
}
