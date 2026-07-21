package koready_backend.place.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.place.application.port.PlaceQueryRepository;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceCursor;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceImageRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceListCriteria;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceSearchCriteria;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class JdbcPlaceQueryRepositoryIntegrationTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 18);

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceQueryRepository repository;

	@BeforeEach
	void migrationIsApplied() {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('2', '3')",
			Integer.class);
		assertEquals(2, count);
	}

	@Test
	void filtersVisiblePlacesByRegionAndStyleWithEnglishFallback() {
		long high = insertPlace("high", "SEOUL", true, true, "95.00");
		insertLocalization(high, "KO", "한국어 자연", "서울 종로구", "한국어 설명");
		insertLocalization(high, "EN", "English Nature", "Jongno-gu, Seoul", "English overview");
		insertStyle(high, "NATURE", "0.9000");

		long fallback = insertPlace("fallback", "SEOUL", true, true, "80.00");
		insertLocalization(fallback, "KO", "한국어만 있는 장소", "서울 중구", "설명");
		insertStyle(fallback, "NATURE", "0.8000");

		long hidden = insertPlace("hidden", "SEOUL", false, true, "99.00");
		insertLocalization(hidden, "KO", "숨김 장소", "서울", null);
		insertStyle(hidden, "NATURE", "1.0000");

		long otherRegion = insertPlace("jeju", "JEJU", true, true, "100.00");
		insertLocalization(otherRegion, "KO", "제주 장소", "제주", null);
		insertStyle(otherRegion, "NATURE", "1.0000");

		List<PlaceRow> rows = repository.findByRegion(new PlaceListCriteria(
			ServiceRegionCode.SEOUL,
			List.of(TravelStyle.NATURE),
			PlaceSort.RECOMMENDED,
			null,
			10,
			PlaceLanguage.EN,
			TODAY));

		assertEquals(List.of(high, fallback), rows.stream().map(PlaceRow::placeId).toList());
		assertEquals("English Nature", rows.getFirst().title());
		assertEquals("한국어만 있는 장소", rows.get(1).title());
		assertEquals("Seoul", rows.get(1).serviceRegionName());
		assertEquals(TravelStyle.NATURE, rows.getFirst().travelStyle());
	}

	@Test
	void usesStableRecommendedCursorWithoutRepeatingRows() {
		long first = placeWithKorean("cursor-first", "90.00", "첫 번째");
		long second = placeWithKorean("cursor-second", "80.00", "두 번째");
		long third = placeWithKorean("cursor-third", "70.00", "세 번째");

		List<PlaceRow> firstPage = repository.findByRegion(criteria(
			PlaceSort.RECOMMENDED, null, 2));
		PlaceRow last = firstPage.getLast();
		List<PlaceRow> secondPage = repository.findByRegion(criteria(
			PlaceSort.RECOMMENDED,
			new PlaceCursor(last.qualityScore(), null, last.placeId()),
			2));

		assertEquals(List.of(first, second), firstPage.stream().map(PlaceRow::placeId).toList());
		assertEquals(List.of(third), secondPage.stream().map(PlaceRow::placeId).toList());
	}

	@Test
	void ordersVisibleFestivalByDeadlineBeforePlacesWithoutOccurrence() {
		long eventPlace = placeWithKorean("event", "50.00", "테스트 축제");
		long plainPlace = placeWithKorean("plain", "99.00", "일반 장소");
		insertOccurrence(eventPlace, TODAY.plusDays(1), TODAY.plusDays(3));

		List<PlaceRow> rows = repository.findByRegion(criteria(
			PlaceSort.DEADLINE, null, 10));

		assertEquals(List.of(eventPlace, plainPlace), rows.stream().map(PlaceRow::placeId).toList());
		assertEquals(TODAY.plusDays(3), rows.getFirst().deadlineSortDate());
		assertEquals(TODAY.plusDays(3), rows.getFirst().festivalOccurrence().endDate());
		assertNull(rows.getLast().festivalOccurrence());

		List<PlaceRow> afterEvent = repository.findByRegion(criteria(
			PlaceSort.DEADLINE,
			new PlaceCursor(new BigDecimal("50.00"), TODAY.plusDays(3), eventPlace),
			10));
		assertEquals(List.of(plainPlace), afterEvent.stream().map(PlaceRow::placeId).toList());

		List<PlaceRow> afterPlain = repository.findByRegion(criteria(
			PlaceSort.DEADLINE,
			new PlaceCursor(new BigDecimal("99.00"), null, plainPlace),
			10));
		assertTrue(afterPlain.isEmpty());
	}

	@Test
	void escapesSqlWildcardsInSearch() {
		long literal = placeWithKorean("literal-percent", "70.00", "100% 로컬시장");
		placeWithKorean("ordinary", "99.00", "평범한 장소");

		List<PlaceRow> rows = repository.search(new PlaceSearchCriteria(
			"%",
			null,
			10,
			PlaceLanguage.KO,
			TODAY));

		assertEquals(List.of(literal), rows.stream().map(PlaceRow::placeId).toList());
		assertEquals("a!!!!!%b!_c", JdbcPlaceQueryRepository.escapeLike("a!!%b_c"));
	}

	@Test
	void detailExposesOnlyVisiblePlaceAndStyleMappingRejectsUnknownEnum() {
		long visible = insertPlace("detail-visible", "SEOUL", true, true, "80.00");
		insertLocalization(visible, "KO", "상세 장소", "서울시", "상세 설명");
		long inactive = insertPlace("detail-inactive", "SEOUL", true, false, "90.00");
		insertLocalization(inactive, "KO", "비활성", "서울시", null);

		assertEquals(
			"KTO_KO",
			repository.findDetail(visible, PlaceLanguage.KO).orElseThrow().translationSource());
		assertFalse(repository.findDetail(inactive, PlaceLanguage.KO).isPresent());
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings (place_id, travel_style, source, confidence)
			VALUES (?, 'UNKNOWN', 'MANUAL', 1.0000)
			""",
			visible));
	}

	@Test
	void ordersAwardedThenRepresentativeThenKtoDetailImagesAndReturnsFour() {
		long placeId = placeWithKorean("gallery", "80.00", "Gallery place");
		insertImage(placeId, "https://example.invalid/representative.jpg", "KTO_DETAIL", 200, 1);
		insertImage(placeId, "https://example.invalid/kto-1.jpg", "KTO_DETAIL", 100, 1);
		insertImage(placeId, "https://example.invalid/kto-2.jpg", "KTO_DETAIL", 100, 2);
		insertImage(placeId, "https://example.invalid/award.jpg", "KTO_PHOTO_AWARD", 300, 1);

		List<PlaceImageRow> images = repository.findImages(placeId);

		assertEquals(List.of(
			"https://example.invalid/award.jpg",
			"https://example.invalid/representative.jpg",
			"https://example.invalid/kto-1.jpg",
			"https://example.invalid/kto-2.jpg"),
			images.stream().map(PlaceImageRow::imageUrl).toList());
	}

	private PlaceListCriteria criteria(PlaceSort sort, PlaceCursor cursor, int limit) {
		return new PlaceListCriteria(
			ServiceRegionCode.SEOUL,
			List.of(),
			sort,
			cursor,
			limit,
			PlaceLanguage.KO,
			TODAY);
	}

	private long placeWithKorean(String sourceId, String score, String title) {
		long placeId = insertPlace(sourceId, "SEOUL", true, true, score);
		insertLocalization(placeId, "KO", title, "서울", title + " 설명");
		return placeId;
	}

	private long insertPlace(
		String sourceId,
		String region,
		boolean showFlag,
		boolean active,
		String score
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, service_region_code, show_flag, active, data_quality_score)
			VALUES (?, ?, ?, ?, ?)
			""",
			sourceId,
			region,
			showFlag,
			active,
			new BigDecimal(score));
		return jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?", Long.class, sourceId);
	}

	private void insertLocalization(
		long placeId,
		String language,
		String title,
		String address,
		String overview
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text, translation_source)
			VALUES (?, ?, ?, ?, ?, ?)
			""",
			placeId,
			language,
			title,
			overview,
			address,
			"KO".equals(language) ? "KTO_KO" : "KTO_EN");
	}

	private void insertStyle(long placeId, String style, String confidence) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings (place_id, travel_style, source, confidence)
			VALUES (?, ?, 'MANUAL', ?)
			""",
			placeId,
			style,
			new BigDecimal(confidence));
	}

	private void insertOccurrence(long placeId, LocalDate startDate, LocalDate endDate) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_event_occurrences
			    (place_id, event_year, occurrence_sequence, start_date, end_date,
			     provider, source_content_id, source_operation, visible_from,
			     date_validation_status)
			VALUES (?, ?, 1, ?, ?, 'MANUAL', ?, 'LOCAL_TEST', ?, 'VALID')
			""",
			placeId,
			startDate.getYear(),
			startDate,
			endDate,
			"event-" + placeId,
			startDate.minusMonths(6));
	}

	private void insertImage(
		long placeId,
		String imageUrl,
		String sourceType,
		int sourcePriority,
		int sourceOrder
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_images
			    (place_id, image_url, image_url_sha256, source_type, source_priority, source_order)
			VALUES (?, ?, ?, ?, ?, ?)
			""",
			placeId,
			imageUrl,
			"a".repeat(63) + sourceOrder,
			sourceType,
			sourcePriority,
			sourceOrder);
	}
}
