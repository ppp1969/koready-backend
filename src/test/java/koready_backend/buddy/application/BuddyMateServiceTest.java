package koready_backend.buddy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.InvalidMateCursorException;
import koready_backend.buddy.application.port.BuddyMateRepository;
import koready_backend.buddy.application.port.BuddyMateRepository.MateCursor;
import koready_backend.buddy.application.port.BuddyMateRepository.MateQuery;
import koready_backend.buddy.application.port.BuddyMateRepository.MateRow;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.application.exception.PlaceNotFoundException;
import koready_backend.place.domain.PlaceLanguage;

class BuddyMateServiceTest {

	private static final Instant FIRST = Instant.parse("2026-07-19T01:00:00Z");
	private static final Instant SECOND = Instant.parse("2026-07-19T02:00:00Z");

	private final BuddyProfileRepository profileRepository =
		mock(BuddyProfileRepository.class);
	private final BuddyMateRepository mateRepository = mock(BuddyMateRepository.class);
	private final BuddyMateService service =
		new BuddyMateService(profileRepository, mateRepository);

	@Test
	void returnsRecentPublicMatesAndBuildsAContextBoundNextCursor() {
		givenViewer(7L, true);
		when(mateRepository.existsVisiblePlace(101L)).thenReturn(true);
		when(mateRepository.findAll(any(MateQuery.class))).thenReturn(List.of(
			row(302L, SECOND, profile(52L, 9L, true, true, true, "Recent")),
			row(301L, FIRST, profile(51L, 8L, true, true, true, "Older"))));

		BuddyMateService.PlaceMatePage result =
			service.getMates("usr_viewer", 101L, null, 1);

		assertEquals(101L, result.placeId());
		assertEquals(List.of("Recent"),
			result.items().stream().map(view -> view.nickname()).toList());
		assertTrue(result.items().getFirst().canMessage());
		assertFalse(result.items().getFirst().blockedByMe());
		assertTrue(result.hasMore());
		assertNotNull(result.nextCursor());

		ArgumentCaptor<MateQuery> query = ArgumentCaptor.forClass(MateQuery.class);
		verify(mateRepository).findAll(query.capture());
		assertEquals(7L, query.getValue().requesterUserId());
		assertEquals(101L, query.getValue().placeId());
		assertNull(query.getValue().cursor());
		assertEquals(2, query.getValue().limit());
	}

	@Test
	void resumesAfterTheLastVisibleSaveAndRejectsAnotherPlaceContext() {
		givenViewer(7L, true);
		when(mateRepository.existsVisiblePlace(101L)).thenReturn(true);
		when(mateRepository.existsVisiblePlace(102L)).thenReturn(true);
		when(mateRepository.findAll(any(MateQuery.class)))
			.thenReturn(List.of(
				row(302L, SECOND, profile(52L, 9L, true, false, true, "First")),
				row(301L, FIRST, profile(51L, 8L, true, false, true, "Second"))))
			.thenReturn(List.of());

		String cursor = service.getMates("usr_viewer", 101L, null, 1).nextCursor();
		BuddyMateService.PlaceMatePage next =
			service.getMates("usr_viewer", 101L, cursor, 1);

		assertTrue(next.items().isEmpty());
		assertFalse(next.hasMore());
		assertNull(next.nextCursor());

		ArgumentCaptor<MateQuery> queries = ArgumentCaptor.forClass(MateQuery.class);
		verify(mateRepository, org.mockito.Mockito.times(2)).findAll(queries.capture());
		MateCursor decoded = queries.getAllValues().get(1).cursor();
		assertEquals(SECOND, decoded.savedAt());
		assertEquals(302L, decoded.savedPlaceId());

		assertThrows(InvalidMateCursorException.class,
			() -> service.getMates("usr_viewer", 102L, cursor, 1));
		assertThrows(InvalidMateCursorException.class,
			() -> service.getMates("usr_viewer", 101L, tamperPosition(cursor), 1));
	}

	@Test
	void hidesPrivateSocialLinksAndDisablesMessagingWithoutAViewerProfile() {
		givenViewer(7L, false);
		when(mateRepository.existsVisiblePlace(101L)).thenReturn(true);
		when(mateRepository.findAll(any(MateQuery.class))).thenReturn(List.of(
			row(301L, FIRST, profile(51L, 8L, true, false, true, "Target"))));

		var result = service.getMates("usr_viewer", 101L, null, 20);

		assertTrue(result.items().getFirst().socialLinks().isEmpty());
		assertFalse(result.items().getFirst().canMessage());
		assertFalse(result.hasMore());
		assertNull(result.nextCursor());
	}

	@Test
	void rejectsUnavailableUsersMissingPlacesInvalidArgumentsAndMalformedCursors() {
		when(profileRepository.findActiveUserId("usr_missing")).thenReturn(Optional.empty());
		assertThrows(BuddyUserUnavailableException.class,
			() -> service.getMates("usr_missing", 101L, null, 20));

		givenViewer(7L, true);
		when(mateRepository.existsVisiblePlace(404L)).thenReturn(false);
		assertThrows(PlaceNotFoundException.class,
			() -> service.getMates("usr_viewer", 404L, null, 20));

		assertThrows(IllegalArgumentException.class,
			() -> service.getMates("usr_viewer", 0L, null, 20));
		assertThrows(IllegalArgumentException.class,
			() -> service.getMates("usr_viewer", 101L, null, 0));
		assertThrows(IllegalArgumentException.class,
			() -> service.getMates("usr_viewer", 101L, null, 51));

		when(mateRepository.existsVisiblePlace(101L)).thenReturn(true);
		assertThrows(InvalidMateCursorException.class,
			() -> service.getMates("usr_viewer", 101L, "not-a-cursor", 20));
		verifyNoInteractionsAfterValidationFailure();
	}

	private void verifyNoInteractionsAfterValidationFailure() {
		// Invalid input and cursor must not reach the page query.
		verify(mateRepository, never()).findAll(any(MateQuery.class));
	}

	private void givenViewer(long userId, boolean hasProfile) {
		when(profileRepository.findActiveUserId("usr_viewer"))
			.thenReturn(Optional.of(userId));
		when(profileRepository.findByUserId(userId)).thenReturn(hasProfile
			? Optional.of(profile(50L, userId, false, false, true, "Viewer"))
			: Optional.empty());
	}

	private static MateRow row(
		long savedPlaceId,
		Instant savedAt,
		BuddyProfileRecord profile
	) {
		return new MateRow(savedPlaceId, savedAt, profile);
	}

	private static String tamperPosition(String cursor) {
		String payload = new String(
			Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		String changed = payload.replace("\t302", "\t999");
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(changed.getBytes(StandardCharsets.UTF_8));
	}

	private static BuddyProfileRecord profile(
		long profileId,
		long userId,
		boolean profilePublic,
		boolean snsPublic,
		boolean allowsMessages,
		String nickname
	) {
		BuddyProfileDraft draft = new BuddyProfileDraft(
			null,
			nickname,
			"France",
			List.of(PlaceLanguage.EN),
			KoreanLevel.BEGINNER,
			"Travel mate",
			List.of(BuddyStyle.FOODIE),
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@mate")),
			profilePublic,
			snsPublic,
			allowsMessages);
		return new BuddyProfileRecord(profileId, userId, draft, FIRST, SECOND);
	}
}
