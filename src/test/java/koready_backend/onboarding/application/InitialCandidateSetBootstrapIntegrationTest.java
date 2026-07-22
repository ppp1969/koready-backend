package koready_backend.onboarding.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.onboarding.domain.InitialCandidatePlace;
import koready_backend.onboarding.domain.InitialCandidatePlaceCatalog;
import koready_backend.place.domain.ServiceRegionCode;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InitialCandidateSetBootstrapIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoCuratedPlaceStore placeStore;

	@Autowired
	InitialCandidateSetBootstrapService bootstrapService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanCuration() {
		jdbcTemplate.update("DELETE FROM onboarding_candidate_current");
		jdbcTemplate.update("DELETE FROM onboarding_candidate_set_items");
		jdbcTemplate.update("DELETE FROM onboarding_candidate_sets");
		jdbcTemplate.update("DELETE FROM admin_audit_logs");
		jdbcTemplate.update("DELETE FROM place_event_occurrences");
		jdbcTemplate.update("DELETE FROM festival_series");
		jdbcTemplate.update("DELETE FROM place_source_matches");
		jdbcTemplate.update("DELETE FROM place_source_records");
		jdbcTemplate.update("DELETE FROM place_style_mappings");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM places");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
	}

	@Test
	void publishesExactlyOneCurrentSetAndReplaysWithoutDuplicates() {
		Map<String, Long> placeIds = importApprovedPlaces();

		InitialCandidateSetBootstrapResult first = bootstrapService.bootstrap(placeIds);
		InitialCandidateSetBootstrapResult replay = bootstrapService.bootstrap(placeIds);

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(1, count("onboarding_candidate_sets"));
		assertEquals(10, count("onboarding_candidate_set_items"));
		assertEquals(1, count("onboarding_candidate_current"));
		assertEquals(3, count("admin_audit_logs"));
		assertEquals("PUBLISHED", value("status", "onboarding_candidate_sets"));
		assertEquals(7, jdbcTemplate.queryForObject(
			"SELECT COUNT(DISTINCT service_region_code) FROM places", Integer.class));
		assertEquals(7, jdbcTemplate.queryForObject(
			"SELECT COUNT(DISTINCT travel_style) FROM place_style_mappings "
				+ "WHERE confidence = 1.0000",
			Integer.class));
		assertEquals(2, jdbcTemplate.queryForObject(
			"SELECT MAX(style_count) FROM ("
				+ "SELECT COUNT(*) AS style_count FROM place_style_mappings "
				+ "WHERE confidence = 1.0000 GROUP BY travel_style) counts",
			Integer.class));
	}

	private Map<String, Long> importApprovedPlaces() {
		Map<String, Long> result = new LinkedHashMap<>();
		for (InitialCandidatePlace place : InitialCandidatePlaceCatalog.approved()) {
			RegionCodes codes = regionCodes(place.serviceRegionCode());
			long placeId = placeStore.upsert(place, detail(place, codes), List.of(
				new KtoPlaceImage("https://example.invalid/detail-" + place.displayOrder() + "-1.jpg", null, null, "Type1", 1),
				new KtoPlaceImage("https://example.invalid/detail-" + place.displayOrder() + "-2.jpg", null, null, "Type1", 2),
				new KtoPlaceImage("https://example.invalid/detail-" + place.displayOrder() + "-3.jpg", null, null, "Type1", 3)));
			result.put(place.ktoContentId(), placeId);
		}
		return Map.copyOf(result);
	}

	private KtoPlaceDetail detail(InitialCandidatePlace place, RegionCodes codes) {
		String suffix = String.format("%02d", place.displayOrder());
		return new KtoPlaceDetail(
			new KtoPlaceItem(
				place.ktoContentId(),
				place.ktoContentTypeId(),
				place.expectedKtoTitleKo(),
				place.serviceRegionCode().name() + " test address",
				null,
				codes.areaCode(),
				"1",
				null,
				null,
				null,
				null,
				"20260101000000",
				"https://example.invalid/" + place.ktoContentId() + ".jpg",
				null,
				"127.0000" + suffix,
				"37.0000" + suffix,
				"6",
				"20260718000000",
				null,
				"00000",
				null,
				codes.legalRegionCode(),
				codes.legalRegionCode() + "100",
				"VE",
				"VE01",
				"VE010100",
				Integer.toHexString(place.displayOrder()).repeat(64)),
			place.expectedKtoTitleKo() + " overview",
			null);
	}

	private RegionCodes regionCodes(ServiceRegionCode region) {
		return switch (region) {
			case SEOUL -> new RegionCodes("1", "11");
			case GYEONGGI -> new RegionCodes("31", "41");
			case GANGWON -> new RegionCodes("32", "51");
			case CHUNGCHEONG -> new RegionCodes("34", "44");
			case JEOLLA -> new RegionCodes("37", "52");
			case GYEONGSANG -> new RegionCodes("6", "26");
			case JEJU -> new RegionCodes("39", "50");
		};
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private String value(String column, String table) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + table, String.class);
	}

	private record RegionCodes(String areaCode, String legalRegionCode) {
	}
}
