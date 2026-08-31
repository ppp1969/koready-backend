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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import koready_backend.editorial.application.EditorialProperties;
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
	void servesOnlyPlacesWithATrustedEnglishMatch() {
		long high = insertPlace("high", "SEOUL", true, true, "95.00");
		insertLocalization(high, "KO", "한국어 자연", "서울 종로구", "한국어 설명");
		insertLocalization(high, "EN", "English Nature", "Jongno-gu, Seoul", "English overview");
		insertStyle(high, "NATURE", "0.9000");

		long fallback = insertPlace("fallback", "SEOUL", true, true, "80.00");
		insertLocalization(fallback, "KO", "한국어만 있는 장소", "서울 중구", "설명");
		insertStyle(fallback, "NATURE", "0.8000");

		long ai = insertPlace("ai", "SEOUL", true, true, "85.00");
		insertLocalization(ai, "KO", "AI 장소", "서울", "AI 이전 한국어 설명");
		insertLocalizationWithSource(
			ai, "EN", "AI Place", "Seoul", "Generated overview", "AI_TRANSLATED");
		insertStyle(ai, "NATURE", "0.8500");
		insertReadyEditorial(ai);

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

		assertEquals(List.of(high, ai), rows.stream().map(PlaceRow::placeId).toList());
		assertEquals("English Nature", rows.getFirst().title());
		assertEquals(TravelStyle.NATURE, rows.getFirst().travelStyle());
		assertTrue(repository.findDetail(fallback, PlaceLanguage.KO).isEmpty());
		assertEquals("AI Place", repository.findDetail(ai, PlaceLanguage.EN).orElseThrow().title());
		assertEquals("Seoul", repository.findDetail(ai, PlaceLanguage.EN).orElseThrow().address());
		assertEquals("AI_TRANSLATED",
			repository.findDetail(ai, PlaceLanguage.EN).orElseThrow().translationSource());
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
			new PlaceCursor(last.curationPriority(), last.qualityScore(), null, last.placeId()),
			2));

		assertEquals(List.of(first, second), firstPage.stream().map(PlaceRow::placeId).toList());
		assertEquals(List.of(third), secondPage.stream().map(PlaceRow::placeId).toList());
	}

	@Test
	void prioritizesAdminCuratedPlacesBeforeQualityScore() {
		long qualityFirst = placeWithKorean("priority-quality", "99.00", "고품질 장소");
		long curatedFirst = placeWithKorean("priority-curated", "10.00", "운영자 선정 장소");
		jdbcTemplate.update(
			"UPDATE places SET curation_priority = 100 WHERE id = ?", curatedFirst);

		List<PlaceRow> rows = repository.findByRegion(criteria(
			PlaceSort.RECOMMENDED, null, 10));

		assertEquals(
			List.of(curatedFirst, qualityFirst),
			rows.stream().map(PlaceRow::placeId).toList());
	}

	@Test
	void exposesOnlyClassifiedPlacesWithImagesAndUsesTheHighestPriorityImage() {
		long ready = placeWithKorean("publication-ready", "70.00", "공개 장소");
		insertImage(ready, "https://example.invalid/award.jpg", "KTO_PHOTO_AWARD", 300, 1);

		long noImage = placeWithKorean("publication-no-image", "99.00", "이미지 없음");
		jdbcTemplate.update("UPDATE places SET first_image_url = NULL WHERE id = ?", noImage);

		long unclassified = insertPlace(
			"publication-unclassified", "SEOUL", true, true, "100.00");
		insertLocalization(unclassified, "KO", "미분류", "서울", "설명");
		insertLocalization(unclassified, "EN", "Unclassified", "Seoul", null);

		List<PlaceRow> rows = repository.findByRegion(criteria(
			PlaceSort.RECOMMENDED, null, 10));

		assertEquals(List.of(ready), rows.stream().map(PlaceRow::placeId).toList());
		assertEquals("https://example.invalid/award.jpg", rows.getFirst().imageUrl());
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
			new PlaceCursor(0, new BigDecimal("50.00"), TODAY.plusDays(3), eventPlace),
			10));
		assertEquals(List.of(plainPlace), afterEvent.stream().map(PlaceRow::placeId).toList());

		List<PlaceRow> afterPlain = repository.findByRegion(criteria(
			PlaceSort.DEADLINE,
			new PlaceCursor(0, new BigDecimal("99.00"), null, plainPlace),
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
		insertLocalization(visible, "EN", "Detail Place", "Seoul", null);
		insertStyle(visible, "NATURE", "1.0000");
		long inactive = insertPlace("detail-inactive", "SEOUL", true, false, "90.00");
		insertLocalization(inactive, "KO", "비활성", "서울시", null);

		assertEquals(
			"KTO_KO",
			repository.findDetail(visible, PlaceLanguage.KO).orElseThrow().translationSource());
		assertFalse(repository.findDetail(inactive, PlaceLanguage.KO).isPresent());
		assertNull(repository.findDetail(visible, PlaceLanguage.EN)
			.orElseThrow()
			.overview());
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings (place_id, travel_style, source, confidence)
			VALUES (?, 'UNKNOWN', 'MANUAL', 1.0000)
			""",
			visible));
	}

	@Test
	void ordersAwardedThenPrimaryThenKtoDetailImagesAndMapsCollectedFacts() {
		long placeId = placeWithKorean("gallery", "80.00", "Gallery place");
		jdbcTemplate.update(
			"UPDATE places SET first_image_url = ? WHERE id = ?",
			"https://example.invalid/primary.jpg",
			placeId);
		insertImage(
			placeId,
			"https://example.invalid/kto-1.jpg",
			"KTO_DETAIL",
			100,
			1);
		insertImage(
			placeId,
			"https://example.invalid/kto-2.jpg",
			"KTO_DETAIL",
			100,
			2);
		insertImage(
			placeId,
			"https://example.invalid/kto-3.jpg",
			"KTO_DETAIL",
			100,
			3);
		insertImage(
			placeId,
			"https://example.invalid/award-1.jpg",
			"KTO_PHOTO_AWARD",
			300,
			1);
		insertImage(
			placeId,
			"https://example.invalid/award-2.jpg",
			"KTO_PHOTO_AWARD",
			300,
			2);
		insertImage(
			placeId,
			"https://example.invalid/award-1.jpg",
			"KTO_DETAIL",
			100,
			4);
		insertAttribute(placeId, "usetime", "09:00-18:00", 1);
		insertAttribute(placeId, "restdate", "Monday", 1);
		insertAttribute(placeId, "normalized_usagefee", "Free", 1);

		List<PlaceImageRow> images = repository.findImages(placeId);
		var facts = repository.findDetailFacts(placeId);

		assertEquals(List.of(
			"https://example.invalid/award-1.jpg",
			"https://example.invalid/award-2.jpg",
			"https://example.invalid/primary.jpg",
			"https://example.invalid/kto-1.jpg"),
			images.stream().map(PlaceImageRow::imageUrl).toList());
		assertEquals("09:00-18:00", facts.operatingHours());
		assertEquals("Monday", facts.closedDays());
		assertEquals("Free", facts.usageFee());
	}

	@Test
	void prioritizesRelatedFallbacksWithTheSameTravelStyle() {
		long source = insertPlace("related-market-source", "SEOUL", true, true, "100.00");
		insertLocalization(source, "KO", "기준 전통시장", "서울", "시장 설명");
		insertLocalization(source, "EN", "Source Market", "Seoul", null);
		insertStyle(source, "TRADITIONAL_MARKET", "1.0000");

		long highPriorityFestival = insertPlace(
			"related-festival", "SEOUL", true, true, "100.00");
		insertLocalization(highPriorityFestival, "KO", "우선 축제", "서울", "축제 설명");
		insertLocalization(highPriorityFestival, "EN", "Priority Festival", "Seoul", null);
		insertStyle(highPriorityFestival, "LOCAL_FESTIVAL", "1.0000");
		insertReadyEditorial(highPriorityFestival);
		jdbcTemplate.update(
			"UPDATE places SET curation_priority = 900 WHERE id = ?",
			highPriorityFestival);

		long sameStyleMarket = insertPlace(
			"related-market", "SEOUL", true, true, "10.00");
		insertLocalization(sameStyleMarket, "KO", "같은 유형 전통시장", "서울", "시장 설명");
		insertLocalization(sameStyleMarket, "EN", "Related Market", "Seoul", null);
		insertStyle(sameStyleMarket, "TRADITIONAL_MARKET", "1.0000");
		insertReadyEditorial(sameStyleMarket);
		jdbcTemplate.update(
			"UPDATE places SET curation_priority = 100 WHERE id = ?",
			sameStyleMarket);

		long unprocessedMarket = insertPlace(
			"related-unprocessed-market", "SEOUL", true, true, "100.00");
		insertLocalization(unprocessedMarket, "KO", "미가공 전통시장", "서울", "시장 설명");
		insertLocalization(unprocessedMarket, "EN", "Unprocessed Market", "Seoul", null);
		insertStyle(unprocessedMarket, "TRADITIONAL_MARKET", "1.0000");
		jdbcTemplate.update(
			"UPDATE places SET curation_priority = 1000 WHERE id = ?",
			unprocessedMarket);

		PlaceQueryRepository strictRepository = new JdbcPlaceQueryRepository(
			new NamedParameterJdbcTemplate(jdbcTemplate),
			new EditorialProperties("koready-place-editorial-v1", true));
		List<PlaceQueryRepository.RelatedPlaceRow> rows =
			strictRepository.findRelatedPlacesWithSameStyle(
				source, PlaceLanguage.KO, List.of(source), 2);

		assertEquals(
			List.of(sameStyleMarket),
			rows.stream().map(PlaceQueryRepository.RelatedPlaceRow::placeId).toList());
		assertNull(rows.getFirst().shortDescription());
		assertEquals(
			TravelStyle.TRADITIONAL_MARKET,
			repository.findPrimaryTravelStyle(source).orElseThrow());
		assertEquals(
			TravelStyle.TRADITIONAL_MARKET,
			repository.findPrimaryTravelStyles(List.of(source, sameStyleMarket))
				.get(sameStyleMarket));
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
		insertLocalization(placeId, "EN", title + " EN", "Seoul", null);
		insertStyle(placeId, "NATURE", "1.0000");
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
			    (kto_content_id, service_region_code, show_flag, active,
			     data_quality_score, first_image_url)
			VALUES (?, ?, ?, ?, ?, ?)
			""",
			sourceId,
			region,
			showFlag,
			active,
			new BigDecimal(score),
			"https://example.invalid/" + sourceId + ".jpg");
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
		insertLocalizationWithSource(
			placeId,
			language,
			title,
			address,
			overview,
			"KO".equals(language) ? "KTO_KO" : "KTO_EN");
	}

	private void insertLocalizationWithSource(
		long placeId,
		String language,
		String title,
		String address,
		String overview,
		String source
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
			source);
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

	private void insertReadyEditorial(long placeId) {
		jdbcTemplate.update("""
			INSERT INTO place_editorial_contents
			    (place_id, source_fingerprint, prompt_version, status, provider, model, generated_at)
			VALUES (?, ?, 'koready-place-editorial-v1', 'READY', 'google-genai', 'test-model', UTC_TIMESTAMP(6))
			""", placeId, "f".repeat(64));
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
				(place_id, image_url, image_url_sha256, source_type,
				 source_priority, source_order)
			VALUES (?, ?, SHA2(?, 256), ?, ?, ?)
			""",
			placeId,
			imageUrl,
			imageUrl,
			sourceType,
			sourcePriority,
			sourceOrder);
	}

	private void insertAttribute(
		long placeId,
		String fieldCode,
		String value,
		int sequence
	) {
		long callId = callLog("detail-" + fieldCode);
		long snapshotId = snapshot(callId, "detail-" + fieldCode);
		jdbcTemplate.update(
			"""
			INSERT INTO place_detail_attributes
				(place_id, source_operation, item_sequence, field_code,
				 value_text, source_content_id, source_snapshot_id, source_hash)
			VALUES (?, 'detailIntro2', ?, ?, ?, 'gallery', ?, ?)
			""",
			placeId,
			sequence,
			fieldCode,
			value,
			snapshotId,
			"c".repeat(64));
	}

	private long callLog(String suffix) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_call_logs
				(provider, api_name, operation, endpoint, request_started_at,
				 success, request_params_masked)
			VALUES ('KTO', 'KOR', 'detailIntro2', ?, UTC_TIMESTAMP(6),
			        TRUE, JSON_OBJECT())
			""",
			"https://example.invalid/" + suffix);
		return jdbcTemplate.queryForObject(
			"SELECT MAX(id) FROM open_api_call_logs",
			Long.class);
	}

	private long snapshot(long callId, String suffix) {
		jdbcTemplate.update(
			"""
			INSERT INTO open_api_raw_snapshots
				(call_log_id, provider, api_name, operation, storage_key,
				 storage_format, content_type, raw_content_sha256,
				 stored_object_sha256, byte_size, compressed_byte_size,
				 item_count, captured_at, retention_class, immutable)
			VALUES (?, 'KTO', 'KOR', 'detailIntro2', ?,
			        'JSON_GZIP', 'application/json', ?, ?, 10, 10, 1,
			        UTC_TIMESTAMP(6), 'DEBUG_TEMPORARY', TRUE)
			""",
			callId,
			"kto/test/" + suffix,
			"d".repeat(64),
			"e".repeat(64));
		return jdbcTemplate.queryForObject(
			"SELECT id FROM open_api_raw_snapshots WHERE call_log_id = ?",
			Long.class,
			callId);
	}
}
