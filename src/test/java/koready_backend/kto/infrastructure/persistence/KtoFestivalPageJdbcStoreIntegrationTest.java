package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.exception.KtoDuplicateContentIdException;
import koready_backend.kto.application.model.KtoFestivalStorePageResult;
import koready_backend.kto.application.model.KtoStoreFestivalPageCommand;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoFestivalPageStore;
import koready_backend.kto.domain.KtoFestivalItem;
import koready_backend.kto.domain.KtoFestivalPage;
import koready_backend.kto.domain.KtoPlaceItem;
import tools.jackson.databind.json.JsonMapper;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class KtoFestivalPageJdbcStoreIntegrationTest {

	private static final Instant REQUESTED_AT = Instant.parse("2026-07-18T03:00:00Z");
	private static final Instant RECEIVED_AT = Instant.parse("2026-07-18T03:00:01Z");
	private static final LocalDate QUERY_START_DATE = LocalDate.of(2026, 7, 1);

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoFestivalPageStore pageStore;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	@BeforeEach
	void cleanStoredFestivals() {
		jdbcTemplate.update("DELETE FROM place_event_occurrences");
		jdbcTemplate.update("DELETE FROM festival_series");
		jdbcTemplate.update("DELETE FROM place_source_matches");
		jdbcTemplate.update("DELETE FROM place_source_records");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM places");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM tour_api_sync_cursors");
	}

	@Test
	void storesAHiddenFestivalWithItsLineageSeriesAndOccurrence() throws Exception {
		KtoFestivalPage page = page(3, "a", item(
			"700001",
			"서울 테스트 축제",
			"11",
			LocalDate.of(2026, 10, 16),
			LocalDate.of(2026, 10, 18),
			"c"));

		KtoFestivalStorePageResult result = pageStore.store(command(page, "page-3", "b"));

		assertFalse(result.replayed());
		assertEquals(1, result.processedCount());
		assertEquals(1, result.occurrenceCount());
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals(1, count("places"));
		assertEquals(1, count("place_localizations"));
		assertEquals(1, count("place_source_records"));
		assertEquals(1, count("place_source_matches"));
		assertEquals(1, count("festival_series"));
		assertEquals(1, count("place_event_occurrences"));

		assertEquals("SEOUL", value("service_region_code", "places"));
		assertEquals(Boolean.TRUE, value(Boolean.class, "show_flag", "places"));
		assertEquals(Boolean.FALSE, value(Boolean.class, "active", "places"));
		assertEquals("KTO:700001", value("series_key", "festival_series"));
		assertEquals(2026, value(Integer.class, "event_year", "place_event_occurrences"));
		assertEquals(
			LocalDate.of(2026, 4, 16),
			value(LocalDate.class, "visible_from", "place_event_occurrences"));
		assertEquals("VALID", value("date_validation_status", "place_event_occurrences"));
		assertEquals("searchFestival2", value("source_operation", "place_event_occurrences"));
		assertEquals("searchFestival2", value("operation", "open_api_call_logs"));

		String maskedParams = value("request_params_masked", "open_api_call_logs");
		assertEquals("***", jsonMapper.readTree(maskedParams).path("serviceKey").asString());
		assertEquals("20260701", jsonMapper.readTree(maskedParams).path("eventStartDate").asString());
		assertEquals("20260701:3", jdbcTemplate.queryForObject(
			"SELECT cursor_value FROM tour_api_sync_cursors WHERE operation = 'searchFestival2'",
			String.class));
	}

	@Test
	void replaysTheSameSnapshotWithoutDuplicatingRows() {
		KtoFestivalPage page = page(1, "a", item(
			"700002", "재실행 축제", "51",
			LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 16), "c"));
		KtoStoreFestivalPageCommand command = command(page, "same", "b");

		KtoFestivalStorePageResult first = pageStore.store(command);
		KtoFestivalStorePageResult replay = pageStore.store(command);

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(first.callLogId(), replay.callLogId());
		assertEquals(first.snapshotId(), replay.snapshotId());
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("places"));
		assertEquals(1, count("festival_series"));
		assertEquals(1, count("place_event_occurrences"));
	}

	@Test
	void keepsDifferentEventYearsSeparateAndUpdatesAnExistingYear() {
		pageStore.store(command(page(1, "a", item(
			"700003", "매년 축제", "11",
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), "c")), "2026-first", "b"));
		pageStore.store(command(page(1, "d", item(
			"700003", "매년 축제", "11",
			LocalDate.of(2027, 9, 2), LocalDate.of(2027, 9, 4), "e")), "2027", "f"));
		pageStore.store(command(page(1, "1", item(
			"700003", "매년 축제", "11",
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), "2")), "2026-update", "3"));

		assertEquals(1, count("places"));
		assertEquals(1, count("festival_series"));
		assertEquals(2, count("place_event_occurrences"));
		assertEquals(
			LocalDate.of(2026, 9, 5),
			jdbcTemplate.queryForObject(
				"SELECT end_date FROM place_event_occurrences WHERE event_year = 2026",
				LocalDate.class));
		assertEquals(
			LocalDate.of(2027, 9, 4),
			jdbcTemplate.queryForObject(
				"SELECT end_date FROM place_event_occurrences WHERE event_year = 2027",
				LocalDate.class));
	}

	@Test
	void rejectsDuplicateContentIdsBeforeWritingMetadata() {
		KtoFestivalItem duplicate = item(
			"700004", "중복 축제", "11",
			LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "c");

		assertThrows(
			KtoDuplicateContentIdException.class,
			() -> pageStore.store(command(page(1, "a", duplicate, duplicate), "duplicate", "b")));
		assertEquals(0, count("open_api_call_logs"));
		assertEquals(0, count("places"));
	}

	@Test
	void storesTwoHundredFestivalItemsInBoundedFlushes() {
		KtoFestivalItem[] items = IntStream.rangeClosed(1, 200)
			.mapToObj(index -> item(
				"800" + String.format("%03d", index),
				"배치 축제 " + index,
				"11",
				LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 2),
				Integer.toHexString(index % 16)))
			.toArray(KtoFestivalItem[]::new);

		KtoFestivalStorePageResult result = pageStore.store(
			command(page(1, "a", items), "full-page", "b"));

		assertEquals(200, result.processedCount());
		assertEquals(200, result.occurrenceCount());
		assertEquals(200, count("places"));
		assertEquals(200, count("festival_series"));
		assertEquals(200, count("place_event_occurrences"));
	}

	@Test
	void mapsTheNewAndCompositeKtoLegalRegionCodes() {
		pageStore.store(command(page(
			1,
			"a",
			item(
				"700005", "통합 지역 축제", "12",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), "c"),
			item(
				"700006", "세종 축제", "36110",
				LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4), "d")),
			"region-codes",
			"b"));

		assertEquals("JEOLLA", jdbcTemplate.queryForObject(
			"SELECT service_region_code FROM places WHERE kto_content_id = '700005'",
			String.class));
		assertEquals("CHUNGCHEONG", jdbcTemplate.queryForObject(
			"SELECT service_region_code FROM places WHERE kto_content_id = '700006'",
			String.class));
	}

	private KtoStoreFestivalPageCommand command(
		KtoFestivalPage page,
		String storageSuffix,
		String objectHashCharacter
	) {
		return new KtoStoreFestivalPageCommand(
			QUERY_START_DATE,
			page,
			new KtoSuccessfulCallMetadata(REQUESTED_AT, RECEIVED_AT, 771, 200),
			new KtoStoredSnapshotMetadata(
				"kto/kor/searchFestival2/2026-07-18/" + storageSuffix + ".json.gz",
				objectHashCharacter.repeat(64),
				35_000,
				RECEIVED_AT));
	}

	private KtoFestivalPage page(
		int pageNumber,
		String pageHashCharacter,
		KtoFestivalItem... items
	) {
		return new KtoFestivalPage(
			pageNumber,
			200,
			items.length,
			List.of(items),
			139_804,
			pageHashCharacter.repeat(64));
	}

	private KtoFestivalItem item(
		String contentId,
		String title,
		String legalRegionCode,
		LocalDate startDate,
		LocalDate endDate,
		String sourceHashCharacter
	) {
		String sourceHash = sourceHashCharacter.repeat(64);
		return new KtoFestivalItem(
			new KtoPlaceItem(
				contentId, "15", title,
				"테스트 주소", "테스트 광장", null, null,
				null, null, null, "Type3", "20260101090000",
				"https://example.invalid/festival.jpg", null,
				"126.978", "37.5665", "6", "20260701090000",
				null, "00000", null, legalRegionCode, "110",
				"EV", "EV01", "EV010100", sourceHash),
			startDate,
			endDate,
			null,
			null);
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
