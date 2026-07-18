package koready_backend.place.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import koready_backend.place.application.exception.SavedPlaceUserUnavailableException;
import koready_backend.place.application.port.PlaceQueryRepository.FestivalOccurrence;
import koready_backend.place.application.port.SavedPlaceRepository;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceCriteria;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceRecord;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceRow;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.SavedPlaceSource;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

class SavedPlaceServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T03:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 7, 19);
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

	private final SavedPlaceRepository repository = mock(SavedPlaceRepository.class);
	private final SavedPlaceService service = new SavedPlaceService(repository, CLOCK);

	@Test
	void savesAVisiblePlaceForTheAuthenticatedUser() {
		when(repository.findActiveUserId("usr_saved")).thenReturn(Optional.of(7L));
		when(repository.existsVisiblePlace(1001L)).thenReturn(true);
		when(repository.save(7L, 1001L, SavedPlaceSource.RECOMMENDATION_CARD, NOW))
			.thenReturn(new SavedPlaceRecord(1001L, NOW));

		SavedPlaceService.SaveResult result = service.save(
			"usr_saved", 1001L, SavedPlaceSource.RECOMMENDATION_CARD);

		assertEquals(1001L, result.placeId());
		assertTrue(result.saved());
		assertEquals(NOW, result.savedAt());
	}

	@Test
	void rejectsAStalePrincipalAndAnUnavailablePlace() {
		when(repository.findActiveUserId("usr_missing")).thenReturn(Optional.empty());
		assertThrows(SavedPlaceUserUnavailableException.class,
			() -> service.save("usr_missing", 1001L, SavedPlaceSource.PLACE_DETAIL));

		when(repository.findActiveUserId("usr_saved")).thenReturn(Optional.of(7L));
		when(repository.existsVisiblePlace(404L)).thenReturn(false);
		assertThrows(PlaceNotFoundException.class,
			() -> service.save("usr_saved", 404L, SavedPlaceSource.PLACE_DETAIL));
	}

	@Test
	void unsavesIdempotentlyWithoutRequiringThePlaceToRemainVisible() {
		when(repository.findActiveUserId("usr_saved")).thenReturn(Optional.of(7L));

		service.unsave("usr_saved", 1001L);

		verify(repository).unsave(7L, 1001L, NOW);
	}

	@Test
	void createsAnOpaqueStableCursorAndMapsSavedCards() {
		when(repository.findActiveUserId("usr_saved")).thenReturn(Optional.of(7L));
		when(repository.findAll(any())).thenReturn(List.of(
			row(103L, 3L, NOW, "Festival", TODAY.minusDays(2), TODAY.plusDays(2)),
			row(102L, 2L, NOW.minusSeconds(1), "Museum", null, null),
			row(101L, 1L, NOW.minusSeconds(2), "Park", null, null)), List.of());

		SavedPlaceService.SavedPlacePage first = service.getSavedPlaces(
			"usr_saved", null, 2, PlaceLanguage.EN);
		service.getSavedPlaces("usr_saved", first.nextCursor(), 2, PlaceLanguage.EN);

		assertTrue(first.hasMore());
		assertEquals(2, first.items().size());
		assertNotNull(first.nextCursor());
		assertFalse(first.nextCursor().contains("2026-07-19"));
		assertTrue(first.items().getFirst().saved());
		assertEquals("ONGOING", first.items().getFirst().festivalOccurrence().status());

		ArgumentCaptor<SavedPlaceCriteria> captor =
			ArgumentCaptor.forClass(SavedPlaceCriteria.class);
		org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2))
			.findAll(captor.capture());
		SavedPlaceCriteria next = captor.getAllValues().get(1);
		assertEquals(102L, next.cursor().savedPlaceId());
		assertEquals(NOW.minusSeconds(1), next.cursor().savedAt());
		assertEquals(3, next.limit());
		assertEquals(TODAY, next.today());
	}

	@Test
	void rejectsACursorWhenTheResponseLanguageChanges() {
		when(repository.findActiveUserId("usr_saved")).thenReturn(Optional.of(7L));
		when(repository.findAll(any())).thenReturn(List.of(
			row(102L, 2L, NOW, "둘", null, null),
			row(101L, 1L, NOW.minusSeconds(1), "하나", null, null)));
		String cursor = service.getSavedPlaces(
			"usr_saved", null, 1, PlaceLanguage.KO).nextCursor();

		assertThrows(InvalidPlaceCursorException.class,
			() -> service.getSavedPlaces("usr_saved", cursor, 1, PlaceLanguage.EN));
	}

	private static SavedPlaceRow row(
		long savedPlaceId,
		long placeId,
		Instant savedAt,
		String title,
		LocalDate startDate,
		LocalDate endDate
	) {
		FestivalOccurrence occurrence = startDate == null
			? null
			: new FestivalOccurrence(700L + placeId, 2026, startDate, endDate);
		return new SavedPlaceRow(
			savedPlaceId,
			placeId,
			savedAt,
			title,
			ServiceRegionCode.SEOUL,
			"Seoul",
			"Jongno-gu, Seoul",
			null,
			TravelStyle.CULTURE_EXPERIENCE,
			"A well-made place overview.",
			new BigDecimal("90.00"),
			occurrence);
	}
}
