package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.model.KtoFetchedDetailOperation;
import koready_backend.kto.application.model.KtoStoreDetailCommand;
import koready_backend.kto.application.model.KtoStoredDetailOperation;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoDetailStore;
import koready_backend.kto.application.port.KtoDetailTargetSource;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailOperationResponse;
import koready_backend.kto.domain.KtoDetailTarget;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KtoDetailJdbcStoreIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoDetailStore store;

	@Autowired
	KtoDetailTargetSource targetSource;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		jdbcTemplate.update("DELETE FROM place_detail_attributes");
		jdbcTemplate.update("DELETE FROM place_images");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM tour_api_sync_cursors");
		jdbcTemplate.update("DELETE FROM places");
	}

	@Test
	void storesFactsImagesLineageAndReplaysWithoutDeletingAwardImages()
		throws Exception {
		long placeId = place("100", "12");
		jdbcTemplate.update(
			"""
			INSERT INTO place_images
				(place_id, image_url, image_url_sha256, source_type,
				 source_priority, source_order)
			VALUES (?, 'https://example.invalid/award.jpg', ?, 'KTO_PHOTO_AWARD', 300, 1)
			""",
			placeId,
			sha256("https://example.invalid/award.jpg".getBytes(StandardCharsets.UTF_8)));
		KtoStoreDetailCommand command = command(placeId);

		store.store(command);
		store.store(command);

		assertEquals(4, count("open_api_call_logs"));
		assertEquals(4, count("open_api_raw_snapshots"));
		assertEquals(8, count("place_detail_attributes"));
		assertEquals(3, count("place_images"));
		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_images WHERE source_type = 'KTO_PHOTO_AWARD'",
			Integer.class));
		assertEquals("09:00-18:00", attribute(placeId, "usetime"));
		assertEquals("Free", attribute(placeId, "infotext"));
		assertEquals("Free", attribute(placeId, "normalized_usagefee"));
		assertEquals("Provider overview", jdbcTemplate.queryForObject(
			"SELECT overview FROM place_localizations WHERE place_id = ? AND language = 'KO'",
			String.class,
			placeId));
		assertEquals(4, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM tour_api_sync_cursors WHERE api_name = 'KOR' AND cursor_value = ?",
			Integer.class,
			Long.toString(placeId)));
	}

	@Test
	void storesRepeatedProviderSerialNumbersInResponseOrder() throws Exception {
		long placeId = place("100", "12");
		KtoStoreDetailCommand command = command(placeId, List.of(
			Map.of(
				"contentid", "100",
				"contenttypeid", "12",
				"serialnum", "1",
				"infoname", "Admission",
				"infotext", "Free"),
			Map.of(
				"contentid", "100",
				"contenttypeid", "12",
				"serialnum", "1",
				"infoname", "Reservation",
				"infotext", "Required")));

		store.store(command);

		assertEquals(List.of(1, 2), jdbcTemplate.queryForList(
			"""
			SELECT item_sequence
			FROM place_detail_attributes
			WHERE place_id = ?
			  AND source_operation = 'detailInfo2'
			  AND field_code = 'infoname'
			ORDER BY item_sequence
			""",
			Integer.class,
			placeId));
	}

	@Test
	void storesDetailSyncCheckpointWithSnapshotLineage() throws Exception {
		long placeId = place("100", "12");

		store.store(command(placeId));
		assertTrue(jdbcTemplate.queryForObject(
			"SELECT show_flag FROM places WHERE id = ?",
			Boolean.class,
			placeId));

		Map<String, Object> checkpoint = jdbcTemplate.queryForMap(
			"""
			SELECT common_snapshot_id, intro_snapshot_id, info_snapshot_id,
			       image_snapshot_id, image_count, completed_at, next_refresh_at
			FROM kto_place_detail_sync_status
			WHERE place_id = ?
			""",
			placeId);
		assertEquals(2, checkpoint.get("image_count"));
		assertEquals(4, jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM open_api_raw_snapshots
			WHERE id IN (?, ?, ?, ?)
			""",
			Integer.class,
			checkpoint.get("common_snapshot_id"),
			checkpoint.get("intro_snapshot_id"),
			checkpoint.get("info_snapshot_id"),
			checkpoint.get("image_snapshot_id")));
		assertEquals(
			jdbcTemplate.queryForObject(
				"SELECT DATE_ADD(completed_at, INTERVAL 30 DAY) FROM kto_place_detail_sync_status WHERE place_id = ?",
				java.time.LocalDateTime.class,
				placeId),
			checkpoint.get("next_refresh_at"));
	}

	@Test
	void storesCheckpointWhenProviderReturnsNoDetailImages() throws Exception {
		long placeId = place("100", "12");

		store.store(command(placeId, List.of(Map.of(
			"contentid", "100",
			"contenttypeid", "12",
			"serialnum", "1",
			"infoname", "Admission",
			"infotext", "Free")), List.of()));

		assertEquals(0, jdbcTemplate.queryForObject(
			"SELECT image_count FROM kto_place_detail_sync_status WHERE place_id = ?",
			Integer.class,
			placeId));
		assertFalse(targetSource.existsAfter(0));
	}

	@Test
	void doesNotPublishAnInactivePlaceAfterDetailStorage() throws Exception {
		long placeId = place("100", "12");
		jdbcTemplate.update(
			"UPDATE places SET active = FALSE WHERE id = ?",
			placeId);

		store.store(command(placeId));

		assertFalse(jdbcTemplate.queryForObject(
			"SELECT show_flag FROM places WHERE id = ?",
			Boolean.class,
			placeId));
	}

	@Test
	void backfillsCheckpointOnlyFromCompleteSnapshotSet() throws Exception {
		long placeId = place("100", "12");
		store.store(command(placeId));
		jdbcTemplate.update("DELETE FROM kto_place_detail_sync_status");
		String migration = new ClassPathResource(
			"db/migration/V26__add_kto_place_detail_sync_status.sql")
			.getContentAsString(StandardCharsets.UTF_8);
		String backfill = migration.substring(migration.indexOf(
			"INSERT INTO kto_place_detail_sync_status"));

		jdbcTemplate.execute(backfill);

		assertEquals(1, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM kto_place_detail_sync_status WHERE place_id = ?",
			Integer.class,
			placeId));
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT image_count FROM kto_place_detail_sync_status WHERE place_id = ?",
			Integer.class,
			placeId));
	}

	@Test
	void skipsFreshCheckpointAndSelectsItAgainAfterRefreshDeadline() throws Exception {
		long completedPlaceId = place("100", "12");
		long pendingPlaceId = place("102", "32");
		store.store(command(completedPlaceId));

		assertEquals(
			List.of(pendingPlaceId),
			targetSource.findAfter(0, 10).stream()
				.map(KtoDetailTarget::placeId)
				.toList());
		assertTrue(targetSource.existsAfter(0));

		jdbcTemplate.update(
			"""
			UPDATE kto_place_detail_sync_status
			SET completed_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 31 DAY),
			    next_refresh_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
			WHERE place_id = ?
			""",
			completedPlaceId);

		assertEquals(
			List.of(completedPlaceId, pendingPlaceId),
			targetSource.findAfter(0, 10).stream()
				.map(KtoDetailTarget::placeId)
				.toList());
	}

	@Test
	void findsTargetsInStablePlaceIdOrder() {
		long first = place("101", "12");
		long second = place("102", "32");

		var targets = targetSource.findAfter(first, 10);

		assertEquals(List.of(second), targets.stream().map(KtoDetailTarget::placeId).toList());
		assertTrue(targetSource.existsAfter(first));
		assertFalse(targetSource.existsAfter(second));
	}

	private KtoStoreDetailCommand command(long placeId) throws Exception {
		return command(placeId, List.of(Map.of(
			"contentid", "100",
			"contenttypeid", "12",
			"serialnum", "1",
			"fldgubun", "1",
			"infoname", "Admission",
			"infotext", "Free")));
	}

	private KtoStoreDetailCommand command(
		long placeId,
		List<Map<String, String>> infoItems
	) throws Exception {
		return command(placeId, infoItems, List.of(
			Map.of(
				"contentid", "100",
				"serialnum", "1",
				"originimgurl", "https://example.invalid/detail-1.jpg",
				"smallimageurl", "https://example.invalid/detail-1-small.jpg"),
			Map.of(
				"contentid", "100",
				"serialnum", "2",
				"originimgurl", "https://example.invalid/detail-2.jpg")));
	}

	private KtoStoreDetailCommand command(
		long placeId,
		List<Map<String, String>> infoItems,
		List<Map<String, String>> imageItems
	) throws Exception {
		KtoDetailTarget target = new KtoDetailTarget(placeId, "100", "12");
		return new KtoStoreDetailCommand(
			target,
			List.of(
				operation(KtoDetailOperation.COMMON, "common", List.of(Map.of(
					"contentid", "100",
					"contenttypeid", "12",
					"title", "Provider title",
					"overview", "Provider overview",
					"addr1", "Seoul",
					"mapx", "126.978",
					"mapy", "37.5665",
					"firstimage", "https://example.invalid/first.jpg"))),
				operation(KtoDetailOperation.INTRO, "intro", List.of(Map.of(
					"contentid", "100",
					"contenttypeid", "12",
					"usetime", "09:00-18:00",
					"restdate", "Monday",
					"parking", "Available",
					"usefee", "Free"))),
				operation(KtoDetailOperation.INFO, "info", infoItems),
				operation(KtoDetailOperation.IMAGE, "image", imageItems)),
			null);
	}

	private KtoStoredDetailOperation operation(
		KtoDetailOperation operation,
		String suffix,
		List<Map<String, String>> items
	) throws Exception {
		byte[] raw = suffix.getBytes(StandardCharsets.UTF_8);
		String rawHash = sha256(raw);
		return new KtoStoredDetailOperation(
			new KtoFetchedDetailOperation(
				new KtoDetailOperationResponse(
					operation, items, raw.length, rawHash),
				new KtoSuccessfulCallMetadata(
					Instant.parse("2026-07-27T00:00:00Z"),
					Instant.parse("2026-07-27T00:00:01Z"),
					1_000,
					200),
				raw),
			new KtoStoredSnapshotMetadata(
				"kto/kor/" + operation.apiName() + "/20260727/" + suffix + ".json.gz",
				"b".repeat(64),
				20,
				Instant.parse("2026-07-27T00:00:02Z")));
	}

	private long place(String contentId, String contentTypeId) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
				(kto_content_id, kto_content_type_id, service_region_code,
				 show_flag, active)
			VALUES (?, ?, 'SEOUL', FALSE, TRUE)
			""",
			contentId,
			contentTypeId);
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			contentId);
	}

	private String attribute(long placeId, String fieldCode) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT value_text
			FROM place_detail_attributes
			WHERE place_id = ? AND field_code = ?
			ORDER BY item_sequence
			LIMIT 1
			""",
			String.class,
			placeId,
			fieldCode);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table,
			Integer.class);
	}

	private String sha256(byte[] value) throws Exception {
		return HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(value));
	}
}
