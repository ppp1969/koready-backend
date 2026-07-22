package koready_backend.place.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import koready_backend.place.application.exception.InvalidPlaceCursorException;
import koready_backend.place.application.exception.PlaceNotFoundException;
import koready_backend.place.application.port.PlaceQueryRepository;
import koready_backend.place.application.port.PlaceQueryRepository.FestivalOccurrence;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceDetailRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceListCriteria;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceSearchCriteria;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.PlaceSort;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

class PlaceQueryServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 7, 18);
	private static final Clock CLOCK = Clock.fixed(
		Instant.parse("2026-07-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));

	private final PlaceQueryRepository repository = mock(PlaceQueryRepository.class);
	private final PlaceQueryService service = new PlaceQueryService(repository, CLOCK);

	@Test
	void createsOpaqueCursorAndDecodesItForTheNextRecommendedPage() {
		when(repository.findByRegion(any())).thenReturn(List.of(
			row(3, "90.00", null),
			row(2, "80.00", null),
			row(1, "70.00", null)), List.of());

		PlaceQueryService.PlacePage first = service.getPlaces(
			ServiceRegionCode.SEOUL,
			List.of(TravelStyle.NATURE, TravelStyle.NATURE),
			PlaceSort.RECOMMENDED,
			null,
			2,
			PlaceLanguage.KO);
		service.getPlaces(
			ServiceRegionCode.SEOUL,
			List.of(TravelStyle.NATURE),
			PlaceSort.RECOMMENDED,
			first.nextCursor(),
			2,
			PlaceLanguage.KO);

		assertTrue(first.hasMore());
		assertEquals(2, first.items().size());
		assertNotNull(first.nextCursor());
		assertFalse(first.nextCursor().contains("80.00"));

		ArgumentCaptor<PlaceListCriteria> captor = ArgumentCaptor.forClass(PlaceListCriteria.class);
		org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
			.findByRegion(captor.capture());
		PlaceListCriteria nextRequest = captor.getAllValues().get(1);
		assertEquals(new BigDecimal("80"), nextRequest.cursor().qualityScore());
		assertEquals(2L, nextRequest.cursor().placeId());
		assertEquals(List.of(TravelStyle.NATURE), nextRequest.travelStyles());
		assertEquals(3, nextRequest.limit());
	}

	@Test
	void rejectsCursorWhenAFilterChanges() {
		when(repository.findByRegion(any())).thenReturn(List.of(
			row(2, "90.00", null),
			row(1, "80.00", null)));
		PlaceQueryService.PlacePage first = service.getPlaces(
			ServiceRegionCode.SEOUL,
			List.of(),
			PlaceSort.RECOMMENDED,
			null,
			1,
			PlaceLanguage.KO);

		assertThrows(InvalidPlaceCursorException.class, () -> service.getPlaces(
			ServiceRegionCode.JEJU,
			List.of(),
			PlaceSort.RECOMMENDED,
			first.nextCursor(),
			1,
			PlaceLanguage.KO));
	}

	@Test
	void normalizesSearchAndMapsUpcomingFestival() {
		FestivalOccurrence occurrence = new FestivalOccurrence(
			71L, 2026, TODAY.plusDays(2), TODAY.plusDays(4));
		PlaceRow festival = new PlaceRow(
			7L,
			"Festival",
			ServiceRegionCode.SEOUL,
			"Seoul",
			"Seoul",
			null,
			TravelStyle.LOCAL_FESTIVAL,
			"A local festival.",
			new BigDecimal("88.00"),
			TODAY.plusDays(4),
			occurrence);
		when(repository.search(any())).thenReturn(List.of(festival));

		PlaceQueryService.PlacePage result = service.search(
			"  local   festival  ", null, 20, PlaceLanguage.EN);

		ArgumentCaptor<PlaceSearchCriteria> captor = ArgumentCaptor.forClass(PlaceSearchCriteria.class);
		org.mockito.Mockito.verify(repository).search(captor.capture());
		assertEquals("local festival", captor.getValue().query());
		assertEquals("UPCOMING", result.items().getFirst().festivalOccurrence().status());
		assertTrue(result.items().getFirst().festivalOccurrence().dateRangeText().contains("2026"));
		assertEquals("A local festival.", result.items().getFirst().shortDescription());
		assertFalse(result.items().getFirst().saved());
	}

	@Test
	void mapsDetailOnlyWhenSourceContentExists() {
		when(repository.findDetail(10L, PlaceLanguage.KO)).thenReturn(Optional.of(
			new PlaceDetailRow(
				10L,
				"Place",
				ServiceRegionCode.GYEONGSANG,
				"경상",
				"주소",
				new BigDecimal("35.1"),
				new BigDecimal("129.1"),
				"https://example.com/place.jpg",
				"첫 문단입니다.\n\n둘째 문단입니다.",
				"MANUAL_EDITED")));

		PlaceQueryService.PlaceDetail detail = service.getPlace(10L, PlaceLanguage.KO);

		assertEquals(List.of("DESCRIPTION", "MATES"), detail.availableTabs());
		assertEquals(2, detail.description().introParagraphs().size());
		assertEquals("MANUAL_EDITED", detail.description().sourceType());
		assertEquals(1, detail.images().size());
		assertFalse(detail.isSaved());

		when(repository.findDetail(11L, PlaceLanguage.KO)).thenReturn(Optional.of(
			new PlaceDetailRow(
				11L, "Empty", ServiceRegionCode.SEOUL, "서울", null,
				null, null, null, null, "KTO_KO")));
		PlaceQueryService.PlaceDetail empty = service.getPlace(11L, PlaceLanguage.KO);
		assertNull(empty.description());
		assertEquals(List.of("MATES"), empty.availableTabs());
	}

	@Test
	void returnsFourOrderedGalleryImagesWhenTheyWereCollectedForThePlace() {
		when(repository.findDetail(10L, PlaceLanguage.KO)).thenReturn(Optional.of(
			new PlaceDetailRow(
				10L, "Place", ServiceRegionCode.SEOUL, "Seoul", "Address",
				null, null, "https://example.invalid/primary.jpg", "Overview", "KTO_KO")));
		when(repository.findImages(10L)).thenReturn(List.of(
			new PlaceQueryRepository.PlaceImageRow("https://example.invalid/first.jpg", "Place 1"),
			new PlaceQueryRepository.PlaceImageRow("https://example.invalid/second.jpg", "Place 2"),
			new PlaceQueryRepository.PlaceImageRow("https://example.invalid/third.jpg", "Place 3"),
			new PlaceQueryRepository.PlaceImageRow("https://example.invalid/fourth.jpg", "Place 4")));

		PlaceQueryService.PlaceDetail detail = service.getPlace(10L, PlaceLanguage.KO);

		assertEquals(List.of(
			"https://example.invalid/first.jpg",
			"https://example.invalid/second.jpg",
			"https://example.invalid/third.jpg",
			"https://example.invalid/fourth.jpg"),
			detail.images().stream().map(PlaceQueryService.PlaceImage::imageUrl).toList());
	}

	@Test
	void reportsMissingPlace() {
		when(repository.findDetail(404L, PlaceLanguage.EN)).thenReturn(Optional.empty());

		assertThrows(PlaceNotFoundException.class,
			() -> service.getPlace(404L, PlaceLanguage.EN));
	}

	@Test
	void resolvesSupportedAcceptLanguageAndFallsBackToKorean() {
		assertEquals(PlaceLanguage.EN, PlaceLanguage.fromAcceptLanguage("fr;q=0.2,en-US;q=0.9"));
		assertEquals(PlaceLanguage.KO, PlaceLanguage.fromAcceptLanguage("ko-KR,en;q=0.5"));
		assertEquals(PlaceLanguage.KO, PlaceLanguage.fromAcceptLanguage("not a valid header;;;"));
		assertEquals(PlaceLanguage.KO, PlaceLanguage.fromAcceptLanguage(null));
	}

	private static PlaceRow row(long id, String score, LocalDate deadline) {
		return new PlaceRow(
			id,
			"Place " + id,
			ServiceRegionCode.SEOUL,
			"서울",
			"서울",
			null,
			TravelStyle.NATURE,
			null,
			new BigDecimal(score),
			deadline,
			null);
	}
}
