package koready_backend.externalapi.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.externalapi.application.port.ExternalApiAdminRepository;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.CallCriteria;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.CallRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SnapshotCriteria;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SnapshotRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SummaryCriteria;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.SnapshotRetentionClass;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcExternalApiAdminRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00.123456Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ExternalApiAdminRepository repository;

	private long successCallId;
	private long failureCallId;
	private long snapshotId;

	@BeforeEach
	void setUp() {
		long jobId = insertJob();
		successCallId = insertCall(true, "searchFestival2", 200, jobId, NOW.minusSeconds(20));
		failureCallId = insertCall(false, "areaBasedList2", 503, null, NOW.minusSeconds(10));
		snapshotId = insertSnapshot(successCallId);
	}

	@Test
	void summarizesCallsProvidersSnapshotsAndRecentFailures() {
		var summary = repository.summarize(new SummaryCriteria(
			NOW.minusSeconds(60), NOW, ExternalApiProvider.KTO));

		assertEquals(2, summary.totalCalls());
		assertEquals(1, summary.successCalls());
		assertEquals(1, summary.failureCalls());
		assertEquals(1, summary.rawSnapshotCount());
		assertEquals(1, summary.providers().size());
		assertEquals(List.of(failureCallId),
			summary.recentFailures().stream().map(CallRecord::id).toList());
	}

	@Test
	void filtersCallPagesAndLoadsSafeSourceMetadata() {
		List<CallRecord> rows = repository.findCallPage(new CallCriteria(
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			true,
			200,
			NOW.minusSeconds(60),
			NOW,
			null,
			true,
			null,
			10));
		CallRecord detail = repository.findCallById(successCallId).orElseThrow();

		assertEquals(List.of(successCallId), rows.stream().map(CallRecord::id).toList());
		assertEquals("***", detail.requestParams().get("serviceKey"));
		assertEquals("KTO_DAILY_SYNC", detail.relatedJobType());
		assertEquals(snapshotId, detail.snapshot().id());
		assertTrue(repository.findCallById(999999L).isEmpty());
	}

	@Test
	void filtersAndLoadsImmutableSnapshotMetadata() {
		List<SnapshotRecord> rows = repository.findSnapshotPage(new SnapshotCriteria(
			ExternalApiProvider.KTO,
			"searchFestival2",
			SnapshotRetentionClass.COMPETITION_EVIDENCE,
			NOW.minusSeconds(60),
			NOW,
			null,
			10));
		SnapshotRecord detail = repository.findSnapshotById(snapshotId).orElseThrow();

		assertEquals(List.of(snapshotId), rows.stream().map(SnapshotRecord::id).toList());
		assertEquals("a".repeat(64), detail.rawContentSha256());
		assertEquals("kto/test/snapshot.json.gz", detail.storageKey());
		assertTrue(detail.immutable());
	}

	private long insertJob() {
		jdbcTemplate.update(
			"""
			INSERT INTO batch_jobs
			    (job_type, status, processed_count, success_count, failure_count,
			     trigger_source, created_at, updated_at)
			VALUES ('KTO_DAILY_SYNC', 'COMPLETED', 1, 1, 0, 'SCHEDULED', ?, ?)
			""",
			java.sql.Timestamp.from(NOW.minusSeconds(30)),
			java.sql.Timestamp.from(NOW.minusSeconds(20)));
		return jdbcTemplate.queryForObject("SELECT MAX(id) FROM batch_jobs", Long.class);
	}

	private long insertCall(
		boolean success,
		String operation,
		int httpStatus,
		Long jobId,
		Instant requestedAt
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_call_logs
			    (provider, api_name, operation, endpoint, request_started_at,
			     response_received_at, duration_ms, success, http_status,
			     request_params_masked, response_summary, external_result_code,
			     item_count, response_bytes, error_message, related_job_id)
			VALUES ('KTO', 'KOR', ?,
			        'https://apis.example.com/search?serviceKey=secret&query=dorm',
			        ?, ?, 1000, ?, ?,
			        JSON_OBJECT('serviceKey', '***', 'pageNo', 1),
			        JSON_OBJECT('resultCode', ?, 'resultMessage', 'OK',
			                    'totalCount', 1, 'itemCount', 1),
			        ?, 1, 1024, ?, ?)
			""",
			operation,
			java.sql.Timestamp.from(requestedAt),
			java.sql.Timestamp.from(requestedAt.plusSeconds(1)),
			success,
			httpStatus,
			success ? "0000" : "ERROR",
			success ? "0000" : "ERROR",
			success ? null : "provider failure",
			jobId);
		return jdbcTemplate.queryForObject("SELECT MAX(id) FROM open_api_call_logs", Long.class);
	}

	private long insertSnapshot(long callLogId) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_raw_snapshots
			    (call_log_id, provider, api_name, operation, storage_key,
			     storage_format, content_type, raw_content_sha256, stored_object_sha256,
			     byte_size, compressed_byte_size, item_count, captured_at,
			     retention_class, retention_until, immutable)
			VALUES (?, 'KTO', 'KOR', 'searchFestival2', 'kto/test/snapshot.json.gz',
			        'JSON_GZIP', 'application/json', ?, ?, 1024, 256, 1, ?,
			        'COMPETITION_EVIDENCE', NULL, TRUE)
			""",
			callLogId,
			"a".repeat(64),
			"b".repeat(64),
			java.sql.Timestamp.from(NOW.minusSeconds(19)));
		return jdbcTemplate.queryForObject(
			"SELECT id FROM open_api_raw_snapshots WHERE call_log_id = ?",
			Long.class,
			callLogId);
	}
}
