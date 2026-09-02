package koready_backend.kto.infrastructure.persistence;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoDuplicateContentIdException;
import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.model.KtoEnglishStorePageCommand;
import koready_backend.kto.application.model.KtoEnglishStorePageResult;
import koready_backend.kto.application.port.KtoEnglishPageStore;
import koready_backend.kto.domain.KtoEnglishMatchDecision;
import koready_backend.kto.domain.KtoEnglishMatchMethod;
import koready_backend.kto.domain.KtoEnglishMatchStatus;
import koready_backend.kto.domain.KtoEnglishSourceQualityClassifier;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.place.domain.EnglishPlaceTitleNormalizer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class KtoEnglishPageJdbcStore implements KtoEnglishPageStore {

	private static final String ENDPOINT =
		"https://apis.data.go.kr/B551011/EngService2/areaBasedSyncList2";
	private static final String MATCHER_VERSION = "kto-en-crosswalk-v1";
	private static final DateTimeFormatter KTO_TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final KtoEnglishSourceQualityClassifier QUALITY_CLASSIFIER =
		new KtoEnglishSourceQualityClassifier();

	private final JdbcTemplate jdbcTemplate;
	private final JsonMapper jsonMapper;
	private final KtoBatchProperties batchProperties;

	public KtoEnglishPageJdbcStore(
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
	public KtoEnglishStorePageResult store(KtoEnglishStorePageCommand command) {
		ExistingSnapshot existing = findExistingSnapshot(command.snapshot().storageKey());
		if (existing != null) {
			return replay(command, existing);
		}
		validateUniqueContentIds(command.matches());
		long callLogId = insertCallLog(command);
		long snapshotId = insertSnapshot(command, callLogId);
		insertSourceRecords(command, snapshotId);
		Map<String, Long> sourceRecordIds = loadSourceRecordIds(snapshotId);
		insertMatches(command.matches(), sourceRecordIds);
		upsertLocalizations(command.matches());
		advancePageCursor(command);

		int automatic = count(command.matches(), KtoEnglishMatchStatus.AUTO_CONFIRMED);
		int review = count(command.matches(), KtoEnglishMatchStatus.REVIEW_REQUIRED);
		int unmatched = count(command.matches(), KtoEnglishMatchStatus.UNMATCHED);
		int localized = countSnapshotLocalizations(snapshotId);
		return new KtoEnglishStorePageResult(
			callLogId,
			snapshotId,
			command.page().items().size(),
			automatic,
			review,
			unmatched,
			localized,
			false);
	}

	private ExistingSnapshot findExistingSnapshot(String storageKey) {
		return jdbcTemplate.query(
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
			storageKey).stream().findFirst().orElse(null);
	}

	private KtoEnglishStorePageResult replay(
		KtoEnglishStorePageCommand command,
		ExistingSnapshot existing
	) {
		if (!existing.rawContentSha256().equals(command.page().responseSha256())
			|| !existing.storedObjectSha256().equals(command.snapshot().storedObjectSha256())
			|| existing.byteSize() != command.page().responseBytes()
			|| existing.compressedByteSize() != command.snapshot().compressedByteSize()
			|| existing.itemCount() != command.page().items().size()) {
			throw new KtoSnapshotConflictException();
		}
		Map<String, Integer> statusCounts = matchStatusCounts(existing.id());
		int automatic = statusCounts.getOrDefault("AUTO_CONFIRMED", 0);
		int review = statusCounts.getOrDefault("REVIEW_REQUIRED", 0);
		int unmatched = existing.itemCount() - automatic - review;
		return new KtoEnglishStorePageResult(
			existing.callLogId(),
			existing.id(),
			existing.itemCount(),
			automatic,
			review,
			unmatched,
			countSnapshotLocalizations(existing.id()),
			true);
	}

	private void validateUniqueContentIds(List<KtoEnglishMatchDecision> matches) {
		var contentIds = new HashSet<String>();
		for (KtoEnglishMatchDecision match : matches) {
			if (!contentIds.add(match.source().contentId())) {
				throw new KtoDuplicateContentIdException();
			}
		}
	}

	private long insertCallLog(KtoEnglishStorePageCommand command) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_call_logs
					(provider, api_name, operation, endpoint, request_started_at,
					 response_received_at, duration_ms, success, http_status,
					 request_params_masked, response_summary, external_result_code,
					 related_job_id, related_job_item_id, item_count, response_bytes)
				VALUES ('KTO', 'ENG', 'areaBasedSyncList2', ?, ?, ?, ?, TRUE, ?,
				        CAST(? AS JSON), CAST(? AS JSON), '0000', ?, ?, ?, ?)
				""",
				Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, ENDPOINT);
			statement.setTimestamp(2, Timestamp.from(command.call().requestedAt()));
			statement.setTimestamp(3, Timestamp.from(command.call().responseReceivedAt()));
			statement.setLong(4, command.call().durationMs());
			statement.setInt(5, command.call().httpStatus());
			statement.setString(6, json(requestSummary(command)));
			statement.setString(7, json(responseSummary(command)));
			if (command.batchExecution() == null) {
				statement.setNull(8, java.sql.Types.BIGINT);
				statement.setNull(9, java.sql.Types.BIGINT);
			} else {
				statement.setLong(8, command.batchExecution().jobId());
				statement.setLong(9, command.batchExecution().jobItemId());
			}
			statement.setInt(10, command.page().items().size());
			statement.setLong(11, command.page().responseBytes());
			return statement;
		}, keyHolder);
		return requiredKey(keyHolder);
	}

	private long insertSnapshot(KtoEnglishStorePageCommand command, long callLogId) {
		var keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
				"""
				INSERT INTO open_api_raw_snapshots
					(call_log_id, provider, api_name, operation, storage_key,
					 storage_format, content_type, raw_content_sha256,
					 stored_object_sha256, byte_size, compressed_byte_size,
					 item_count, captured_at, retention_class, immutable)
				VALUES (?, 'KTO', 'ENG', 'areaBasedSyncList2', ?, 'JSON_GZIP',
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

	private void insertSourceRecords(KtoEnglishStorePageCommand command, long snapshotId) {
		if (command.matches().isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO place_source_records
				(provider, api_name, operation, source_content_id, source_old_content_id,
				 language, raw_snapshot_id, source_modified_time, source_hash,
				 source_quality, quality_warnings, quality_classified_at,
				 quality_classifier_version, captured_at)
			VALUES ('KTO', 'ENG', 'areaBasedSyncList2', ?, ?, 'EN', ?, ?, ?,
			        ?, CAST(? AS JSON), ?, ?, ?)
			""",
			command.matches(),
			batchProperties.flushSize(),
			(statement, match) -> {
				var quality = QUALITY_CLASSIFIER.classify(
					match.source().title(),
					joinAddress(match.source().address1(), match.source().address2()));
				statement.setString(1, match.source().contentId());
				statement.setString(2, match.source().oldContentId());
				statement.setLong(3, snapshotId);
				statement.setObject(4, modifiedTime(match.source().modifiedTime()));
				statement.setString(5, match.source().sourceHash());
				statement.setString(6, quality.quality().name());
				statement.setString(7, json(quality.warnings().stream()
					.map(Enum::name)
					.sorted()
					.toList()));
				statement.setTimestamp(8, Timestamp.from(command.snapshot().capturedAt()));
				statement.setString(9, KtoEnglishSourceQualityClassifier.VERSION);
				statement.setTimestamp(10, Timestamp.from(command.snapshot().capturedAt()));
			});
	}

	private Map<String, Long> loadSourceRecordIds(long snapshotId) {
		Map<String, Long> ids = new HashMap<>();
		jdbcTemplate.query(
			"""
			SELECT id, source_content_id
			FROM place_source_records
			WHERE raw_snapshot_id = ? AND language = 'EN'
			""",
			(rs, rowNumber) -> {
				ids.put(rs.getString("source_content_id"), rs.getLong("id"));
				return 0;
			},
			snapshotId);
		return Map.copyOf(ids);
	}

	private void insertMatches(
		List<KtoEnglishMatchDecision> decisions,
		Map<String, Long> sourceRecordIds
	) {
		List<MatchRow> rows = new ArrayList<>();
		for (KtoEnglishMatchDecision decision : decisions) {
			if (decision.status() == KtoEnglishMatchStatus.UNMATCHED) {
				continue;
			}
			String status = decision.status().name();
			String method = decision.method().name();
			String evidence = json(Map.of(
				"imageCandidateCount", decision.imageCandidateCount(),
				"coordinateCandidateCount", decision.coordinateCandidateCount(),
				"conflict", decision.status() == KtoEnglishMatchStatus.REVIEW_REQUIRED));
			for (var candidate : decision.candidates()) {
				rows.add(new MatchRow(
					sourceRecordIds.get(decision.source().contentId()),
					candidate.placeId(),
					method,
					confidence(decision.method(), decision.candidates().size()),
					decision.candidates().size(),
					evidence,
					status));
			}
		}
		if (rows.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO place_source_matches
				(source_record_id, place_id, match_method, confidence,
				 candidate_count, evidence_json, status, matcher_version)
			VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
			""",
			rows,
			batchProperties.flushSize(),
			(statement, row) -> {
				statement.setLong(1, row.sourceRecordId());
				statement.setLong(2, row.placeId());
				statement.setString(3, row.method());
				statement.setBigDecimal(4, row.confidence());
				statement.setInt(5, row.candidateCount());
				statement.setString(6, row.evidence());
				statement.setString(7, row.status());
				statement.setString(8, MATCHER_VERSION);
			});
	}

	private void upsertLocalizations(List<KtoEnglishMatchDecision> decisions) {
		List<KtoEnglishMatchDecision> localized = decisions.stream()
			.filter(decision -> decision.status() == KtoEnglishMatchStatus.AUTO_CONFIRMED)
			.filter(decision -> decision.source().title() != null)
			.toList();
		if (localized.isEmpty()) {
			return;
		}
		jdbcTemplate.batchUpdate(
			"""
			INSERT INTO place_localizations
				(place_id, language, title, address_text, translation_source,
				 source_content_id, source_hash)
			VALUES (?, 'EN', ?, ?, 'KTO_EN', ?, ?)
			ON DUPLICATE KEY UPDATE
				title = IF(
					translation_source = 'MANUAL_EDITED'
					OR (translation_source = 'KTO_EN' AND source_content_id <> VALUES(source_content_id)),
					title, VALUES(title)),
				address_text = IF(
					translation_source = 'MANUAL_EDITED'
					OR (translation_source = 'KTO_EN' AND source_content_id <> VALUES(source_content_id)),
					address_text, VALUES(address_text)),
				source_content_id = IF(
					translation_source = 'MANUAL_EDITED'
					OR (translation_source = 'KTO_EN' AND source_content_id <> VALUES(source_content_id)),
					source_content_id, VALUES(source_content_id)),
				source_hash = IF(
					translation_source = 'MANUAL_EDITED'
					OR (translation_source = 'KTO_EN' AND source_content_id <> VALUES(source_content_id)),
					source_hash, VALUES(source_hash)),
				translation_source = IF(translation_source = 'MANUAL_EDITED',
					translation_source, 'KTO_EN')
			""",
			localized,
			batchProperties.flushSize(),
			(statement, decision) -> {
				statement.setLong(1, decision.confirmedPlaceId());
				statement.setString(2, EnglishPlaceTitleNormalizer.normalize(decision.source().title()));
				statement.setString(3, joinAddress(
					decision.source().address1(), decision.source().address2()));
				statement.setString(4, decision.source().contentId());
				statement.setString(5, decision.source().sourceHash());
			});
	}

	private int countSnapshotLocalizations(long snapshotId) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(DISTINCT source.id)
			FROM place_source_records source
			JOIN place_source_matches matches
			  ON matches.source_record_id = source.id
			 AND matches.status = 'AUTO_CONFIRMED'
			JOIN place_localizations localized
			  ON localized.place_id = matches.place_id
			 AND localized.language = 'EN'
			 AND localized.translation_source = 'KTO_EN'
			 AND localized.source_content_id = source.source_content_id
			WHERE source.raw_snapshot_id = ?
			""",
			Integer.class,
			snapshotId);
	}

	private Map<String, Integer> matchStatusCounts(long snapshotId) {
		Map<String, Integer> counts = new HashMap<>();
		jdbcTemplate.query(
			"""
			SELECT matches.status, COUNT(DISTINCT matches.source_record_id) AS item_count
			FROM place_source_matches matches
			JOIN place_source_records source ON source.id = matches.source_record_id
			WHERE source.raw_snapshot_id = ?
			GROUP BY matches.status
			""",
			(rs, rowNumber) -> {
				counts.put(rs.getString("status"), rs.getInt("item_count"));
				return 0;
			},
			snapshotId);
		return Map.copyOf(counts);
	}

	private void advancePageCursor(KtoEnglishStorePageCommand command) {
		jdbcTemplate.update(
			"""
			INSERT INTO tour_api_sync_cursors
				(provider, api_name, operation, cursor_type, cursor_value,
				 last_success_at, failure_count, enabled)
			VALUES ('KTO', 'ENG', 'areaBasedSyncList2', 'PAGE', ?, ?, 0, TRUE)
			ON DUPLICATE KEY UPDATE
				cursor_value = IF(
					cursor_value IS NULL,
					VALUES(cursor_value),
					GREATEST(CAST(cursor_value AS UNSIGNED),
						CAST(VALUES(cursor_value) AS UNSIGNED))),
				last_success_at = VALUES(last_success_at),
				last_failure_at = NULL,
				failure_count = 0,
				enabled = TRUE
			""",
			Integer.toString(command.page().pageNumber()),
			Timestamp.from(command.snapshot().capturedAt()));
	}

	private Map<String, Object> requestSummary(KtoEnglishStorePageCommand command) {
		return Map.of(
			"numOfRows", command.page().pageSize(),
			"pageNo", command.page().pageNumber(),
			"MobileOS", "ETC",
			"MobileApp", "KoReady",
			"_type", "json",
			"serviceKey", "***");
	}

	private Map<String, Object> responseSummary(KtoEnglishStorePageCommand command) {
		return Map.of(
			"resultCode", "0000",
			"pageNo", command.page().pageNumber(),
			"numOfRows", command.page().pageSize(),
			"totalCount", command.page().totalCount(),
			"itemCount", command.page().items().size(),
			"responseSha256", command.page().responseSha256());
	}

	private int count(List<KtoEnglishMatchDecision> decisions, KtoEnglishMatchStatus status) {
		return (int) decisions.stream().filter(decision -> decision.status() == status).count();
	}

	private java.math.BigDecimal confidence(KtoEnglishMatchMethod method, int candidateCount) {
		if (method == KtoEnglishMatchMethod.IMAGE_PATH) {
			return new java.math.BigDecimal("1.0000");
		}
		if (method == KtoEnglishMatchMethod.COORDINATE_CONTENT_TYPE) {
			return new java.math.BigDecimal("0.9800");
		}
		return java.math.BigDecimal.ONE
			.divide(java.math.BigDecimal.valueOf(candidateCount), 4, java.math.RoundingMode.HALF_UP);
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

	private String joinAddress(String address1, String address2) {
		if (address1 == null) {
			return address2;
		}
		return address2 == null ? address1 : address1 + " " + address2;
	}

	private long requiredKey(GeneratedKeyHolder keyHolder) {
		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Database did not return a generated key");
		}
		return key.longValue();
	}

	private String json(Object value) {
		try {
			return jsonMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("KTO English metadata could not be serialized");
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

	private record MatchRow(
		long sourceRecordId,
		long placeId,
		String method,
		java.math.BigDecimal confidence,
		int candidateCount,
		String evidence,
		String status
	) {
	}
}
