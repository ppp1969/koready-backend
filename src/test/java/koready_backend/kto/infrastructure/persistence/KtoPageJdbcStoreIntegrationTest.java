package koready_backend.kto.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.kto.application.exception.KtoDuplicateContentIdException;
import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.model.KtoStorePageCommand;
import koready_backend.kto.application.model.KtoStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoPageStore;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.domain.KtoSyncPage;
import tools.jackson.databind.json.JsonMapper;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
class KtoPageJdbcStoreIntegrationTest {

	private static final Instant REQUESTED_AT = Instant.parse("2026-07-17T03:00:00Z");
	private static final Instant RECEIVED_AT = Instant.parse("2026-07-17T03:00:01Z");

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	KtoPageStore pageStore;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JsonMapper jsonMapper;

	@BeforeEach
	void cleanStoredPages() {
		jdbcTemplate.update("DELETE FROM place_source_matches");
		jdbcTemplate.update("DELETE FROM place_source_records");
		jdbcTemplate.update("DELETE FROM place_localizations");
		jdbcTemplate.update("DELETE FROM places");
		jdbcTemplate.update("DELETE FROM open_api_raw_snapshots");
		jdbcTemplate.update("DELETE FROM open_api_call_logs");
		jdbcTemplate.update("DELETE FROM tour_api_sync_cursors");
	}

	@Test
	void storesCallSnapshotPlacesLineageAndCursorInOnePage() {
		KtoSyncPage page = page(
			3,
			"a",
			item("100001", "서울 테스트 장소", "1", "1", "c", "126.978", "37.5665"),
			item("100002", "미지 지역 비노출 장소", "99", "0", "d", "181", "91"),
			item("100003", null, null, "1", "e", null, null));

		KtoStorePageResult result = pageStore.store(command(page, "page-3", "b"));

		assertFalse(result.replayed());
		assertEquals(3, result.processedCount());
		assertEquals(1, result.activeCount());
		assertEquals(2, result.localizationCount());
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals(3, count("places"));
		assertEquals(2, count("place_localizations"));
		assertEquals(3, count("place_source_records"));
		assertEquals(3, count("place_source_matches"));

		String maskedParams = jdbcTemplate.queryForObject(
			"SELECT request_params_masked FROM open_api_call_logs",
			String.class);
		assertEquals("***", jsonMapper.readTree(maskedParams).path("serviceKey").asString());
		assertFalse(maskedParams.contains("test-service-key"));
		assertEquals("a".repeat(64), jdbcTemplate.queryForObject(
			"SELECT raw_content_sha256 FROM open_api_raw_snapshots",
			String.class));

		assertEquals("SEOUL", value("service_region_code", "100001"));
		assertEquals("1", value("area_code", "100001"));
		assertEquals(Boolean.TRUE, booleanValue("active", "100001"));
		assertEquals("서울특별시 테스트구 테스트로 1", value("address", "100001"));
		assertNull(value("service_region_code", "100002"));
		assertEquals("99", value("area_code", "100002"));
		assertEquals(Boolean.FALSE, booleanValue("show_flag", "100002"));
		assertNull(value("latitude", "100002"));
		assertNull(value("longitude", "100002"));
		assertEquals(Boolean.FALSE, booleanValue("active", "100003"));
		assertEquals("3", jdbcTemplate.queryForObject(
			"SELECT cursor_value FROM tour_api_sync_cursors WHERE operation = 'areaBasedSyncList2'",
			String.class));
	}

	@Test
	void updatesAPlaceFromANewerSnapshotWithoutDuplicatingTheMaster() {
		KtoSyncPage first = page(1, "a", item("200001", "이전 제목", "1", "1", "c", "126", "37"));
		KtoSyncPage second = page(1, "f", item("200001", "새 제목", "2", "0", "d", "127", "38"));

		pageStore.store(command(first, "first", "b"));
		pageStore.store(command(second, "second", "e"));

		assertEquals(1, count("places"));
		assertEquals(1, count("place_localizations"));
		assertEquals(2, count("open_api_raw_snapshots"));
		assertEquals(2, count("place_source_records"));
		assertEquals("새 제목", jdbcTemplate.queryForObject(
			"SELECT title FROM place_localizations",
			String.class));
		assertEquals("GYEONGGI", value("service_region_code", "200001"));
		assertEquals(Boolean.FALSE, booleanValue("show_flag", "200001"));
		assertEquals(Boolean.FALSE, booleanValue("active", "200001"));
	}

	@Test
	void replaysTheSameSnapshotWithoutCreatingDuplicateRows() {
		KtoSyncPage page = page(1, "a", item("300001", "재실행 장소", "1", "1", "c", "126", "37"));
		KtoStorePageCommand command = command(page, "same", "b");

		KtoStorePageResult first = pageStore.store(command);
		KtoStorePageResult replay = pageStore.store(command);

		assertFalse(first.replayed());
		assertTrue(replay.replayed());
		assertEquals(first.callLogId(), replay.callLogId());
		assertEquals(first.snapshotId(), replay.snapshotId());
		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals(1, count("places"));
		assertEquals(1, count("place_source_records"));
	}

	@Test
	void rejectsAStorageKeyCollisionWithDifferentRawContent() {
		KtoStorePageCommand first = command(
			page(1, "a", item("400001", "첫 장소", "1", "1", "c", "126", "37")),
			"collision",
			"b");
		KtoStorePageCommand conflict = command(
			page(1, "f", item("400002", "다른 장소", "1", "1", "d", "126", "37")),
			"collision",
			"e");
		pageStore.store(first);

		assertThrows(KtoSnapshotConflictException.class, () -> pageStore.store(conflict));

		assertEquals(1, count("open_api_call_logs"));
		assertEquals(1, count("open_api_raw_snapshots"));
		assertEquals(1, count("places"));
	}

	@Test
	void rejectsDuplicateContentIdsBeforeWritingPageMetadata() {
		KtoPlaceItem duplicate = item("450001", "중복 장소", "1", "1", "c", "126", "37");
		KtoSyncPage page = page(1, "a", duplicate, duplicate);

		assertThrows(KtoDuplicateContentIdException.class, () -> pageStore.store(command(page, "duplicate", "b")));

		assertEquals(0, count("open_api_call_logs"));
		assertEquals(0, count("open_api_raw_snapshots"));
		assertEquals(0, count("places"));
	}

	@Test
	void doesNotMoveThePageCursorBackwards() {
		pageStore.store(command(
			page(3, "a", item("460003", "3페이지 장소", "1", "1", "c", "126", "37")),
			"cursor-3",
			"b"));
		pageStore.store(command(
			page(2, "d", item("460002", "2페이지 장소", "1", "1", "e", "126", "37")),
			"cursor-2",
			"f"));

		assertEquals("3", jdbcTemplate.queryForObject(
			"SELECT cursor_value FROM tour_api_sync_cursors WHERE operation = 'areaBasedSyncList2'",
			String.class));
	}

	@Test
	void rollsBackTheWholePageWhenAProcessedValueBreaksTheDatabaseContract() {
		KtoSyncPage page = page(
			5,
			"a",
			item("500001", "정상 장소", "1", "1", "c", "126", "37"),
			item("500002", "가".repeat(301), "1", "1", "d", "126", "37"));

		assertThrows(DataIntegrityViolationException.class, () -> pageStore.store(command(page, "rollback", "b")));

		assertEquals(0, count("open_api_call_logs"));
		assertEquals(0, count("open_api_raw_snapshots"));
		assertEquals(0, count("places"));
		assertEquals(0, count("tour_api_sync_cursors"));
	}

	@Test
	void storesTheConfiguredTwoHundredItemPageInBoundedBatches() {
		KtoPlaceItem[] items = IntStream.rangeClosed(1, 200)
			.mapToObj(index -> item(
				"600" + String.format("%03d", index),
				"배치 장소 " + index,
				"1",
				"1",
				"c",
				"126",
				"37"))
			.toArray(KtoPlaceItem[]::new);

		KtoStorePageResult result = pageStore.store(command(page(1, "a", items), "full-page", "b"));

		assertEquals(200, result.processedCount());
		assertEquals(200, result.activeCount());
		assertEquals(200, count("places"));
		assertEquals(200, count("place_source_records"));
		assertEquals(200, count("place_source_matches"));
	}

	private KtoStorePageCommand command(KtoSyncPage page, String storageSuffix, String objectHashCharacter) {
		return new KtoStorePageCommand(
			page,
			new KtoSuccessfulCallMetadata(REQUESTED_AT, RECEIVED_AT, 771, 200),
			new KtoStoredSnapshotMetadata(
				"kto/kor/areaBasedSyncList2/2026-07-17/" + storageSuffix + ".json.gz",
				objectHashCharacter.repeat(64),
				35_000,
				RECEIVED_AT));
	}

	private KtoSyncPage page(int pageNumber, String pageHashCharacter, KtoPlaceItem... items) {
		return new KtoSyncPage(
			pageNumber,
			200,
			68_524,
			List.of(items),
			139_804,
			pageHashCharacter.repeat(64));
	}

	private KtoPlaceItem item(
		String contentId,
		String title,
		String areaCode,
		String showFlag,
		String sourceHashCharacter,
		String longitude,
		String latitude
	) {
		return new KtoPlaceItem(
			contentId,
			"12",
			title,
			"서울특별시 테스트구",
			"테스트로 1",
			areaCode,
			"1",
			"A01",
			"A0101",
			"A01010100",
			"Type3",
			"20260101090000",
			"https://example.invalid/image.jpg",
			"https://example.invalid/thumbnail.jpg",
			longitude,
			latitude,
			"6",
			"20260701090000",
			null,
			"00000",
			showFlag,
			"11",
			"110",
			"VE",
			"VE01",
			"VE010100",
			sourceHashCharacter.repeat(64));
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private String value(String column, String contentId) {
		return jdbcTemplate.queryForObject(
			"SELECT " + column + " FROM places WHERE kto_content_id = ?",
			String.class,
			contentId);
	}

	private Boolean booleanValue(String column, String contentId) {
		return jdbcTemplate.queryForObject(
			"SELECT " + column + " FROM places WHERE kto_content_id = ?",
			Boolean.class,
			contentId);
	}
}
