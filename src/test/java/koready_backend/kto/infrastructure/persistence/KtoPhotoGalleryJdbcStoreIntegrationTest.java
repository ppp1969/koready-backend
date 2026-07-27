package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import koready_backend.kto.application.KtoPhotoGalleryCurationService;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoPhotoGalleryStore;
import koready_backend.kto.domain.KtoPhotoGalleryItem;
import koready_backend.kto.domain.KtoPhotoGalleryPage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KtoPhotoGalleryJdbcStoreIntegrationTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-27T05:00:00Z");
	private static final Instant RECEIVED_AT =
		Instant.parse("2026-07-27T05:00:01Z");
	private static final String PAGE_HASH = "a".repeat(64);
	private static final String STORED_HASH = "b".repeat(64);

	@Container
	@ServiceConnection
	static final MySQLContainer mysql =
		new MySQLContainer("mysql:8.4");

	@Autowired
	KtoPhotoGalleryStore store;

	@Autowired
	KtoPhotoGalleryCurationService curationService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM place_images");
		jdbcTemplate.update(
			"DELETE FROM kto_photo_gallery_place_mappings");
		jdbcTemplate.update("DELETE FROM kto_photo_gallery_images");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM places");
	}

	@Test
	void storesIdempotentlyAndPublishesOnlyAfterOperatorApproval() {
		var first = store.store(command());
		var replay = store.store(command());

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(2, count("kto_photo_gallery_images"));
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals(0, count("place_images"));

		long placeId = place();
		curationService.approveMapping(
			"gallery-001",
			new KtoPhotoGalleryCurationService.ApproveMappingCommand(
				placeId,
				2,
				"The operator verified the place and usage rights."),
			"operator");
		curationService.approveMapping(
			"gallery-001",
			new KtoPhotoGalleryCurationService.ApproveMappingCommand(
				placeId,
				2,
				"The operator repeated the verification."),
			"operator");

		assertEquals(1, count("kto_photo_gallery_place_mappings"));
		assertEquals(1, count("place_images"));
		assertEquals("KTO_PHOTO_GALLERY",
			jdbcTemplate.queryForObject(
				"SELECT source_type FROM place_images",
				String.class));
		assertEquals(250, jdbcTemplate.queryForObject(
			"SELECT source_priority FROM place_images",
			Integer.class));
		assertEquals(
			"OPERATOR_APPROVED",
			curationService.list(null, true, 0, 20)
				.items().getFirst().rightsStatus());

		curationService.removeMapping(
			"gallery-001",
			"The approved mapping was incorrect.",
			"operator");

		assertEquals(0, count("kto_photo_gallery_place_mappings"));
		assertEquals(0, count("place_images"));
		assertEquals(2, count("kto_photo_gallery_images"));
	}

	private KtoPhotoGalleryStorePageCommand command() {
		return new KtoPhotoGalleryStorePageCommand(
			new KtoPhotoGalleryPage(
				1,
				200,
				312,
				List.of(
					item("gallery-001", "Palace in autumn"),
					item("gallery-002", "Coastal trail")),
				2048,
				PAGE_HASH),
			new KtoSuccessfulCallMetadata(
				REQUESTED_AT, RECEIVED_AT, 1000, 200),
			new KtoStoredSnapshotMetadata(
				"kto/photo-gallery/galleryList1/20260727/"
					+ "event-start-20260727-page-1-aaaaaaaaaaaaaaaa.json.gz",
				STORED_HASH,
				1024,
				RECEIVED_AT.plusSeconds(1)),
			null);
	}

	private KtoPhotoGalleryItem item(
		String contentId,
		String title
	) {
		return new KtoPhotoGalleryItem(
			contentId,
			"17",
			title,
			"Seoul",
			"10",
			"KTO",
			"palace,autumn,seoul",
			"https://example.invalid/photo-gallery/" + contentId + ".jpg",
			"20240102030405",
			"20250102030405",
			PAGE_HASH);
	}

	private long place() {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active)
			VALUES ('place-001', 'SEOUL', TRUE, TRUE)
			""");
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = 'place-001'",
			Long.class);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source)
			VALUES (?, 'KO', 'Gyeongbokgung Palace', 'KTO_KO')
			""",
			placeId);
		return placeId;
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table,
			Integer.class);
	}
}
