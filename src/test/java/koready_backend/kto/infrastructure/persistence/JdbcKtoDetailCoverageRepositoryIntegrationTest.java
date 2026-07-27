package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.port.KtoDetailCoverageRepository;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JdbcKtoDetailCoverageRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoDetailCoverageRepository repository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM place_images");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM places");
	}

	@Test
	void summarizesCheckpointsProviderImagesAndEffectiveGallery() throws Exception {
		long fallback = place("coverage-1", "https://example.invalid/fallback.jpg");
		long empty = place("coverage-2", null);
		long one = place("coverage-3", null);
		long two = place("coverage-4", null);
		long three = place("coverage-5", null);
		long four = place("coverage-6", null);
		place("coverage-pending", null);

		checkpoint(fallback, 0, false);
		checkpoint(empty, 0, false);
		checkpoint(one, 1, false);
		checkpoint(two, 2, true);
		checkpoint(three, 3, false);
		checkpoint(four, 5, false);

		image(one, "https://example.invalid/one.jpg", "KTO_DETAIL", 100, 1);
		image(two, "https://example.invalid/two-a.jpg", "KTO_DETAIL", 100, 1);
		image(two, "https://example.invalid/two-a.jpg", "MANUAL", 200, 1);
		image(two, "https://example.invalid/two-b.jpg", "KTO_DETAIL", 100, 2);
		for (int index = 1; index <= 3; index++) {
			image(three, "https://example.invalid/three-" + index + ".jpg",
				"KTO_DETAIL", 100, index);
		}
		for (int index = 1; index <= 5; index++) {
			image(four, "https://example.invalid/four-" + index + ".jpg",
				"KTO_DETAIL", 100, index);
		}

		var result = repository.summarize();

		assertEquals(7, result.totalPlaces());
		assertEquals(6, result.completedPlaces());
		assertEquals(1, result.dueForRefreshPlaces());
		assertEquals(
			new KtoDetailCoverageRepository.ImageBuckets(2, 1, 1, 1, 1),
			result.ktoDetailImages());
		assertEquals(
			new KtoDetailCoverageRepository.ImageBuckets(1, 2, 1, 1, 1),
			result.effectiveGalleryImages());
	}

	private long place(String contentId, String firstImageUrl) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
				(kto_content_id, kto_content_type_id, service_region_code,
				 first_image_url, show_flag, active)
			VALUES (?, '12', 'SEOUL', ?, TRUE, TRUE)
			""",
			contentId,
			firstImageUrl);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private void checkpoint(long placeId, int imageCount, boolean due) {
		long snapshotId = snapshot(placeId);
		Instant completedAt = Instant.now().minus(2, ChronoUnit.DAYS);
		Instant nextRefreshAt = due
			? Instant.now().minus(1, ChronoUnit.DAYS)
			: Instant.now().plus(28, ChronoUnit.DAYS);
		jdbcTemplate.update(
			"""
			INSERT INTO kto_place_detail_sync_status
				(place_id, common_snapshot_id, intro_snapshot_id,
				 info_snapshot_id, image_snapshot_id, image_count,
				 completed_at, next_refresh_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			placeId,
			snapshotId,
			snapshotId,
			snapshotId,
			snapshotId,
			imageCount,
			Timestamp.from(completedAt),
			Timestamp.from(nextRefreshAt));
	}

	private long snapshot(long placeId) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_call_logs
				(provider, api_name, operation, endpoint, request_started_at,
				 response_received_at, duration_ms, success, http_status)
			VALUES ('KTO', 'KOR', 'detailCommon2', 'https://example.invalid',
			        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1, TRUE, 200)
			""");
		long callId = jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM open_api_call_logs",
			Long.class);
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_raw_snapshots
				(call_log_id, provider, api_name, operation, storage_key,
				 storage_format, content_type, raw_content_sha256,
				 stored_object_sha256, byte_size, compressed_byte_size,
				 item_count, captured_at, retention_class, immutable)
			VALUES (?, 'KTO', 'KOR', 'detailCommon2', ?, 'JSON_GZIP',
			        'application/json', ?, ?, 1, 1, 1,
			        CURRENT_TIMESTAMP(6), 'COMPETITION_EVIDENCE', TRUE)
			""",
			callId,
			"coverage/" + placeId,
			"a".repeat(64),
			"b".repeat(64));
		return jdbcTemplate.queryForObject(
			"SELECT id FROM open_api_raw_snapshots WHERE call_log_id = ?",
			Long.class,
			callId);
	}

	private void image(
		long placeId,
		String url,
		String sourceType,
		int priority,
		int order
	) throws Exception {
		jdbcTemplate.update(
			"""
			INSERT INTO place_images
				(place_id, image_url, image_url_sha256, source_type,
				 source_priority, source_order)
			VALUES (?, ?, ?, ?, ?, ?)
			""",
			placeId,
			url,
			sha256(url),
			sourceType,
			priority,
			order);
	}

	private String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
