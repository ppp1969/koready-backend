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
import koready_backend.kto.application.port.KtoRelatedTourRegionSource;
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
	KtoRelatedTourRegionSource regionSource;

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
		jdbcTemplate.update("DELETE FROM place_style_mappings");
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
		long duplicateAmbiguousPlaceId =
			place("related-3b", "모호한 장소", "11", "11530");
		changeStyle(ambiguousRelatedPlaceId, "LOCAL_FOOD");
		changeStyle(duplicateAmbiguousPlaceId, "LOCAL_FOOD");
		hideFromPublication(ambiguousRelatedPlaceId);
		hideFromPublication(duplicateAmbiguousPlaceId);
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

	@Test
	void indexesTheExactKoreanTitleLookupUsedByBulkMatching() {
		Integer indexColumns = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = 'place_localizations'
			  AND index_name = 'idx_place_localizations_language_title'
			""",
			Integer.class);

		assertEquals(3, indexColumns);
	}

	@Test
	void resolvesProviderRegionFromBaseMonthUsingOfficialCodeHistory() {
		place("current-mokpo", "Current Mokpo", "12", "110");
		place("current-seohae", "Current Seohae", "28", "275");

		var historicalJeonnam = regionSource.findAfter(
			"202606", "11:99999", 1).getFirst();
		var currentJeonnam = regionSource.findAfter(
			"202607", "11:99999", 1).getFirst();
		var historicalIncheon = regionSource.findAfter(
			"202606", "27:99999", 1).getFirst();

		assertEquals("12:12110", historicalJeonnam.key());
		assertEquals("46", historicalJeonnam.providerAreaCode());
		assertEquals("46110", historicalJeonnam.providerSignguCode());
		assertEquals("12", currentJeonnam.providerAreaCode());
		assertEquals("12110", currentJeonnam.providerSignguCode());
		assertEquals("28:28275", historicalIncheon.key());
		assertEquals("28", historicalIncheon.providerAreaCode());
		assertEquals("28260", historicalIncheon.providerSignguCode());
	}

	@Test
	void matchesHistoricalRegionRecordsToCurrentPlacesThroughOfficialAlias() {
		long historicalSource =
			place("historical-source", "Historical Source", "12", "110");
		long historicalRelated =
			place("historical-related", "Historical Related", "12", "130");
		KtoRelatedTourPage page = new KtoRelatedTourPage(
			1,
			200,
			1,
			List.of(new KtoRelatedTourItem(
				"202606",
				"46",
				"Jeollanam-do",
				"46110",
				"Mokpo-si",
				"5".repeat(32),
				"Historical Source",
				"6".repeat(32),
				"Historical Related",
				"46",
				"Jeollanam-do",
				"46130",
				"Yeosu-si",
				"Tour",
				null,
				null,
				1,
				PAGE_HASH)),
			2048,
			"e".repeat(64));

		store.store(new KtoRelatedTourStorePageCommand(
			"202606",
			new KtoRelatedTourRegion(
				"12", "12110", "46", "46110"),
			page,
			new KtoSuccessfulCallMetadata(
				REQUESTED_AT, RECEIVED_AT, 1000, 200),
			new KtoStoredSnapshotMetadata(
				"kto/related-tour/areaBasedList12026064646110/"
					+ "20260727/page-1-eeeeeeeeeeeeeeee.json.gz",
				"f".repeat(64),
				1024,
				RECEIVED_AT.plusSeconds(1)),
			null));

		assertEquals(1, count("kto_related_tour_mappings"));
		assertEquals(
			historicalSource,
			jdbcTemplate.queryForObject(
				"SELECT source_place_id FROM kto_related_tour_mappings",
				Long.class));
		assertEquals(
			historicalRelated,
			jdbcTemplate.queryForObject(
				"SELECT related_place_id FROM kto_related_tour_mappings",
				Long.class));
	}

	@Test
	void refreshesAutoMappingsInBulkWithoutOverwritingManualReview() {
		store.store(command());
		long ambiguousRecordId = recordId("4".repeat(32));
		curationService.confirmMapping(
			ambiguousRecordId,
			new KtoRelatedTourCurationService.ConfirmMappingCommand(
				sourcePlaceId,
				ambiguousRelatedPlaceId,
				"운영진이 모호한 장소를 확인했습니다."),
			"operator");
		place(
			"related-1-duplicate",
			"첫 번째 장소",
			"11",
			"11530");

		store.store(command(
			"c".repeat(64),
			"d".repeat(64),
			"page-1-cccccccccccccccc.json.gz"));

		assertEquals(2, count("kto_related_tour_mappings"));
		assertEquals(2, count("place_relations"));
		assertEquals(
			"MANUAL_CONFIRMED",
			jdbcTemplate.queryForObject(
				"""
				SELECT match_status
				FROM kto_related_tour_mappings
				WHERE related_tour_record_id = ?
				""",
				String.class,
				ambiguousRecordId));
		assertEquals(
			0,
			jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM kto_related_tour_mappings mapping
				JOIN kto_related_tour_records record
				    ON record.id = mapping.related_tour_record_id
				WHERE record.related_tour_code = ?
				""",
				Integer.class,
				"2".repeat(32)));
	}

	private KtoRelatedTourStorePageCommand command() {
		return command(
			PAGE_HASH,
			STORED_HASH,
			"page-1-aaaaaaaaaaaaaaaa.json.gz");
	}

	private KtoRelatedTourStorePageCommand command(
		String pageHash,
		String storedHash,
		String fileName
	) {
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
				pageHash),
			new KtoSuccessfulCallMetadata(
				REQUESTED_AT, RECEIVED_AT, 1000, 200),
			new KtoStoredSnapshotMetadata(
				"kto/related-tour/areaBasedList12026061111530/"
					+ "20260727/" + fileName,
				storedHash,
				1024,
				RECEIVED_AT.plusSeconds(1)),
			null);
	}

	private long recordId(String relatedTourCode) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT id
			FROM kto_related_tour_records
			WHERE related_tour_code = ?
			""",
			Long.class,
			relatedTourCode);
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
			     show_flag, active, first_image_url)
			VALUES (?, 'SEOUL', ?, ?, TRUE, TRUE, ?)
			""",
			ktoContentId,
			regionCode,
			signguCode,
			"https://example.invalid/" + ktoContentId + ".jpg");
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
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, translation_source)
			VALUES (?, 'EN', ?, 'KTO_EN')
			""",
			placeId,
			"English " + ktoContentId);
		jdbcTemplate.update(
			"INSERT INTO place_style_mappings "
				+ "(place_id, travel_style, source, confidence, is_primary) "
				+ "VALUES (?, 'NATURE', 'MANUAL', 1.0000, TRUE)",
			placeId);
		return placeId;
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table,
			Integer.class);
	}

	private void changeStyle(long placeId, String travelStyle) {
		jdbcTemplate.update(
			"UPDATE place_style_mappings SET travel_style = ? WHERE place_id = ?",
			travelStyle,
			placeId);
	}

	private void hideFromPublication(long placeId) {
		jdbcTemplate.update(
			"UPDATE places SET show_flag = FALSE WHERE id = ?",
			placeId);
	}
}
