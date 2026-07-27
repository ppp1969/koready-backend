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

import koready_backend.kto.application.KtoRelatedTourCurationService;
import koready_backend.kto.application.model.KtoRelatedTourRegion;
import koready_backend.kto.application.model.KtoRelatedTourStorePageCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoRelatedTourStore;
import koready_backend.kto.domain.KtoRelatedTourItem;
import koready_backend.kto.domain.KtoRelatedTourPage;
import koready_backend.place.application.PlaceQueryService;
import koready_backend.place.domain.PlaceLanguage;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KtoRelatedTourJdbcStoreIntegrationTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-27T06:00:00Z");
	private static final Instant RECEIVED_AT =
		Instant.parse("2026-07-27T06:00:01Z");
	private static final String PAGE_HASH = "a".repeat(64);
	private static final String STORED_HASH = "b".repeat(64);

	@Container
	@ServiceConnection
	static final MySQLContainer mysql =
		new MySQLContainer("mysql:8.4");

	@Autowired
	KtoRelatedTourStore store;

	@Autowired
	KtoRelatedTourCurationService curationService;

	@Autowired
	PlaceQueryService placeQueryService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long sourcePlaceId;
	long firstRelatedPlaceId;
	long secondRelatedPlaceId;
	long ambiguousRelatedPlaceId;

	@BeforeEach
	void cleanAndSeedPlaces() {
		jdbcTemplate.update("DELETE FROM place_relations");
		jdbcTemplate.update("DELETE FROM kto_related_tour_mappings");
		jdbcTemplate.update("DELETE FROM kto_related_tour_records");
		jdbcTemplate.update("DELETE FROM place_images");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM places");

		sourcePlaceId = place("source", "기준 장소", "11", "11530");
		firstRelatedPlaceId =
			place("related-1", "첫 번째 장소", "11", "11530");
		secondRelatedPlaceId =
			place("related-2", "두 번째 장소", "11", "11530");
		ambiguousRelatedPlaceId =
			place("related-3a", "모호한 장소", "11", "11530");
		place("related-3b", "모호한 장소", "11", "11530");
	}

	@Test
	void storesIdempotentlyAutoMatchesOnlyUniqueNamesAndSupportsReview() {
		var first = store.store(command());
		var replay = store.store(command());

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(3, count("kto_related_tour_records"));
		assertEquals(2, count("kto_related_tour_mappings"));
		assertEquals(2, count("place_relations"));
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));

		var detail = placeQueryService.getPlace(
			sourcePlaceId, PlaceLanguage.KO);
		assertEquals(
			List.of(firstRelatedPlaceId, secondRelatedPlaceId),
			detail.relatedPlaces().stream()
				.map(PlaceQueryService.RelatedPlace::placeId)
				.toList());

		long ambiguousRecordId = jdbcTemplate.queryForObject(
			"""
			SELECT id
			FROM kto_related_tour_records
			WHERE related_tour_code = ?
			""",
			Long.class,
			"4".repeat(32));
		curationService.confirmMapping(
			ambiguousRecordId,
			new KtoRelatedTourCurationService.ConfirmMappingCommand(
				sourcePlaceId,
				ambiguousRelatedPlaceId,
				"운영진이 장소명과 지역을 확인했습니다."),
			"operator");

		assertEquals(3, count("kto_related_tour_mappings"));
		assertEquals(3, count("place_relations"));
		assertEquals(
			"MANUAL_CONFIRMED",
			curationService.list(null, "MANUAL_CONFIRMED", 0, 20)
				.items().getFirst().matchStatus());

		curationService.removeMapping(
			ambiguousRecordId,
			"잘못 연결된 장소를 해제합니다.",
			"operator");

		assertEquals(2, count("kto_related_tour_mappings"));
		assertEquals(2, count("place_relations"));
		assertEquals(3, count("kto_related_tour_records"));
	}

	private KtoRelatedTourStorePageCommand command() {
		return new KtoRelatedTourStorePageCommand(
			"202606",
			new KtoRelatedTourRegion("11", "11530"),
			new KtoRelatedTourPage(
				1,
				200,
				3,
				List.of(
					item("2", "첫 번째 장소", 1),
					item("3", "두 번째 장소", 2),
					item("4", "모호한 장소", 3)),
				2048,
				PAGE_HASH),
			new KtoSuccessfulCallMetadata(
				REQUESTED_AT, RECEIVED_AT, 1000, 200),
			new KtoStoredSnapshotMetadata(
				"kto/related-tour/areaBasedList12026061111530/"
					+ "20260727/page-1-aaaaaaaaaaaaaaaa.json.gz",
				STORED_HASH,
				1024,
				RECEIVED_AT.plusSeconds(1)),
			null);
	}

	private KtoRelatedTourItem item(
		String relatedCodeDigit,
		String relatedName,
		int rank
	) {
		return new KtoRelatedTourItem(
			"202606",
			"11",
			"서울특별시",
			"11530",
			"구로구",
			"1".repeat(32),
			"기준 장소",
			relatedCodeDigit.repeat(32),
			relatedName,
			"11",
			"서울특별시",
			"11530",
			"구로구",
			"관광지",
			"문화관광",
			"전시시설",
			rank,
			PAGE_HASH);
	}

	private long place(
		String ktoContentId,
		String title,
		String regionCode,
		String signguCode
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code,
			     ldong_regn_cd, ldong_signgu_cd,
			     show_flag, active)
			VALUES (?, 'SEOUL', ?, ?, TRUE, TRUE)
			""",
			ktoContentId,
			regionCode,
			signguCode);
		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			ktoContentId);
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview,
			     translation_source)
			VALUES (?, 'KO', ?, ?, 'KTO_KO')
			""",
			placeId,
			title,
			title + " 설명");
		return placeId;
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table,
			Integer.class);
	}
}
