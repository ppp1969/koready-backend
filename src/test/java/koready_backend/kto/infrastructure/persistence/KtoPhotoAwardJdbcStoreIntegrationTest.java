package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.KtoPhotoAwardCurationService;
import koready_backend.kto.application.exception.KtoPhotoAwardMappingConflictException;
import koready_backend.kto.application.model.KtoPhotoAwardStorePageCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoPhotoAwardStore;
import koready_backend.kto.domain.KtoPhotoAwardItem;
import koready_backend.kto.domain.KtoPhotoAwardPage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KtoPhotoAwardJdbcStoreIntegrationTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-27T01:00:00Z");
	private static final Instant RECEIVED_AT =
		Instant.parse("2026-07-27T01:00:01Z");
	private static final String PAGE_HASH =
		"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
	private static final String STORED_HASH =
		"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoPhotoAwardStore store;

	@Autowired
	KtoPhotoAwardCurationService curationService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM place_images");
		jdbcTemplate.update("DELETE FROM kto_photo_award_place_mappings");
		jdbcTemplate.update("DELETE FROM kto_photo_awards");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM places");
	}

	@Test
	void importsIdempotentlyAndOnlyCreatesAPriorityImageAfterExplicitApproval() {
		var first = store.store(command());
		var replay = store.store(command());

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(2, count("kto_photo_awards"));
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals(0, count("place_images"));

		long placeId = place();
		curationService.approveMapping(
			"award-001",
			new KtoPhotoAwardCurationService.ApproveMappingCommand(
				placeId, 1, "운영진이 원본 촬영 장소를 확인함"),
			"operator");
		curationService.approveMapping(
			"award-001",
			new KtoPhotoAwardCurationService.ApproveMappingCommand(
				placeId, 1, "운영진이 원본 촬영 장소를 다시 확인함"),
			"operator");

		assertEquals(1, count("kto_photo_award_place_mappings"));
		assertEquals(1, count("place_images"));
		assertEquals("KTO_PHOTO_AWARD", jdbcTemplate.queryForObject(
			"SELECT source_type FROM place_images",
			String.class));
		assertEquals(300, jdbcTemplate.queryForObject(
			"SELECT source_priority FROM place_images",
			Integer.class));
		assertEquals("https://example.invalid/award-001.jpg",
			jdbcTemplate.queryForObject(
				"SELECT image_url FROM place_images",
				String.class));
		assertThrows(
			KtoPhotoAwardMappingConflictException.class,
			() -> curationService.approveMapping(
				"award-002",
				new KtoPhotoAwardCurationService.ApproveMappingCommand(
					placeId, 1, "이미 사용 중인 순서는 덮어쓰지 않음"),
				"operator"));
		assertEquals(1, count("kto_photo_award_place_mappings"));

		curationService.removeMapping(
			"award-001", "잘못 연결된 장소를 해제함", "operator");

		assertEquals(0, count("kto_photo_award_place_mappings"));
		assertEquals(0, count("place_images"));
		assertEquals(3, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM admin_audit_logs
			WHERE resource_type = 'KTO_PHOTO_AWARD_MAPPING'
			""",
			Integer.class));
	}

	private KtoPhotoAwardStorePageCommand command() {
		return new KtoPhotoAwardStorePageCommand(
			new KtoPhotoAwardPage(
				1,
				200,
				96,
				List.of(
					item("award-001", "궁궐의 아침",
						"https://example.invalid/award-001.jpg"),
					item("award-002", "바다와 일출",
						"https://example.invalid/award-002.jpg")),
				2048,
				PAGE_HASH),
			new KtoSuccessfulCallMetadata(
				REQUESTED_AT, RECEIVED_AT, 1000, 200),
			new KtoStoredSnapshotMetadata(
				"kto/photo-award/phokoAwrdSyncList/20260727/"
					+ "event-start-20260727-page-1-aaaaaaaaaaaaaaaa.json.gz",
				STORED_HASH,
				1024,
				RECEIVED_AT.plusSeconds(1)),
			null);
	}

	private KtoPhotoAwardItem item(
		String contentId,
		String title,
		String imageUrl
	) {
		return new KtoPhotoAwardItem(
			contentId,
			title,
			"촬영지",
			"키워드",
			"English title",
			"Film location",
			"keyword",
			imageUrl,
			null,
			"Type1",
			PAGE_HASH);
	}

	private long place() {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active)
			VALUES ('place-001', 'SEOUL', TRUE, TRUE)
			""");
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = 'place-001'",
			Long.class);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table,
			Integer.class);
	}
}
