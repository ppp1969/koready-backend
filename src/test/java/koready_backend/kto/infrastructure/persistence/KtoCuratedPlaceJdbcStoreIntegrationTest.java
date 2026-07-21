package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import koready_backend.kto.application.port.KtoCuratedPlaceStore;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.domain.KtoPhotoAwardImage;
import koready_backend.onboarding.domain.InitialCandidatePlace;
import koready_backend.onboarding.domain.InitialCandidatePlaceCatalog;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KtoCuratedPlaceJdbcStoreIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoCuratedPlaceStore store;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanPlaces() {
		jdbcTemplate.update("DELETE FROM onboarding_candidate_current");
		jdbcTemplate.update("DELETE FROM onboarding_candidate_set_items");
		jdbcTemplate.update("DELETE FROM onboarding_candidate_sets");
		jdbcTemplate.update("DELETE FROM place_event_occurrences");
		jdbcTemplate.update("DELETE FROM festival_series");
		jdbcTemplate.update("DELETE FROM place_source_matches");
		jdbcTemplate.update("DELETE FROM place_source_records");
		jdbcTemplate.update("DELETE FROM place_style_mappings");
		jdbcTemplate.update("DELETE FROM place_images");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM places");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
	}

	@Test
	void upsertsAnApprovedPlaceWithTwoLocalizationsAndTheManualTravelStyle() {
		InitialCandidatePlace specification = InitialCandidatePlaceCatalog.approved().getFirst();
		KtoPlaceDetail detail = detail(specification, "1", "11");

		List<KtoPlaceImage> images = fourReadyImages();
		long firstId = store.upsert(specification, detail, images);
		long replayId = store.upsert(specification, detail, images);

		assertEquals(firstId, replayId);
		assertEquals(1, count("places"));
		assertEquals(2, count("place_localizations"));
		assertEquals(1, count("place_style_mappings"));
		assertEquals(4, count("place_images"));
		assertEquals(4, jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM place_images WHERE source_type = 'KTO_DETAIL'", Integer.class));
		assertEquals("SEOUL", value("service_region_code", "places"));
		assertEquals(Boolean.TRUE, value(Boolean.class, "active", "places"));
		assertEquals(Boolean.TRUE, value(Boolean.class, "show_flag", "places"));
		assertEquals("https://www.royalpalace.go.kr", value("homepage", "places"));
		assertEquals(specification.travelStyle().name(), value("travel_style", "place_style_mappings"));
		assertEquals("MANUAL", value("source", "place_style_mappings"));
		assertEquals(specification.titleEn(), jdbcTemplate.queryForObject(
			"SELECT title FROM place_localizations WHERE language = 'EN'", String.class));
		assertEquals("MANUAL_EDITED", jdbcTemplate.queryForObject(
			"SELECT translation_source FROM place_localizations WHERE language = 'EN'", String.class));
		assertEquals("경복궁 소개", jdbcTemplate.queryForObject(
			"SELECT overview FROM place_localizations WHERE language = 'KO'", String.class));
	}

	@Test
	void makesTheApprovedStyleWinOverAnExistingAutomaticStyle() {
		InitialCandidatePlace specification = InitialCandidatePlaceCatalog.approved().getFirst();
		long placeId = store.upsert(specification, detail(specification, "1", "11"), fourReadyImages());
		jdbcTemplate.update(
			"INSERT INTO place_style_mappings (place_id, travel_style, source, confidence) "
				+ "VALUES (?, 'NATURE', 'AI', 1.0000)",
			placeId);

		store.upsert(specification, detail(specification, "1", "11"), fourReadyImages());

		String selected = jdbcTemplate.queryForObject(
			"SELECT travel_style FROM place_style_mappings WHERE place_id = ? "
				+ "ORDER BY confidence DESC, travel_style ASC LIMIT 1",
			String.class,
			placeId);
		assertEquals(specification.travelStyle().name(), selected);
		assertTrue(jdbcTemplate.queryForObject(
			"SELECT confidence < 1 FROM place_style_mappings "
				+ "WHERE place_id = ? AND travel_style = 'NATURE'",
			Boolean.class,
			placeId));
	}

	@Test
	void rejectsAProviderRegionThatDoesNotMatchTheApprovedRegion() {
		InitialCandidatePlace specification = InitialCandidatePlaceCatalog.approved().getFirst();

		assertThrows(
			IllegalStateException.class,
			() -> store.upsert(specification, detail(specification, "31", "41")));
		assertEquals(0, count("places"));
	}

	private static KtoPlaceDetail detail(
		InitialCandidatePlace specification,
		String areaCode,
		String legalRegionCode
	) {
		return new KtoPlaceDetail(
			new KtoPlaceItem(
				specification.ktoContentId(),
				specification.ktoContentTypeId(),
				specification.expectedKtoTitleKo(),
				"서울특별시 종로구 사직로 161",
				null,
				areaCode,
				"23",
				null,
				null,
				null,
				null,
				"20040218000000",
				"https://example.invalid/gyeongbokgung.jpg",
				null,
				"126.9769930",
				"37.5788222",
				"6",
				"20260701090000",
				null,
				"03045",
				null,
				legalRegionCode,
				"11110",
				"VE",
				"VE01",
				"VE010100",
				"a".repeat(64)),
			"경복궁 소개",
			"https://www.royalpalace.go.kr");
	}

	@Test
	void storesAnApprovedPhotoAwardFirstAndKeepsExactlyFourDistinctImages() {
		InitialCandidatePlace specification = InitialCandidatePlaceCatalog.approved().getFirst();
		var award = new KtoPhotoAwardImage(
			"award-1", "https://example.invalid/award.jpg", null,
			"Award", "Type1", true, 1);
		List<KtoPlaceImage> details = List.of(
			new KtoPlaceImage("https://example.invalid/award.jpg", null, "duplicate", "Type1", 1),
			new KtoPlaceImage("https://example.invalid/detail-1.jpg", null, "Detail 1", "Type1", 2),
			new KtoPlaceImage("https://example.invalid/detail-2.jpg", null, "Detail 2", "Type1", 3));

		long placeId = store.upsert(
			specification, detail(specification, "1", "11"), details, List.of(award));

		assertEquals(4, count("place_images"));
		assertEquals(List.of(
			"https://example.invalid/award.jpg",
			"https://example.invalid/gyeongbokgung.jpg",
			"https://example.invalid/detail-1.jpg",
			"https://example.invalid/detail-2.jpg"), jdbcTemplate.queryForList(
			"SELECT image_url FROM place_images WHERE place_id = ? "
				+ "ORDER BY source_priority DESC, source_order ASC, id ASC",
			String.class, placeId));
		assertEquals("KTO_PHOTO_AWARD", jdbcTemplate.queryForObject(
			"SELECT source_type FROM place_images WHERE image_url = ?",
			String.class, "https://example.invalid/award.jpg"));
	}

	private static List<KtoPlaceImage> fourReadyImages() {
		return List.of(
			new KtoPlaceImage("https://example.invalid/gallery-1.jpg", null, "Gallery 1", "Type1", 1),
			new KtoPlaceImage("https://example.invalid/gallery-2.jpg", null, "Gallery 2", "Type1", 2),
			new KtoPlaceImage("https://example.invalid/gallery-3.jpg", null, "Gallery 3", "Type1", 3));
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private String value(String column, String table) {
		return value(String.class, column, table);
	}

	private <T> T value(Class<T> type, String column, String table) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + table, type);
	}
}
