package koready_backend.buddy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.application.port.BuddyBlockRepository;
import koready_backend.buddy.application.port.BuddyBlockRepository.BlockRelationship;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

class BuddyPublicProfileServiceTest {

	private static final Instant CREATED_AT = Instant.parse("2026-07-18T03:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-07-19T03:00:00Z");

	private final BuddyProfileRepository profileRepository =
		mock(BuddyProfileRepository.class);
	private final BuddyBlockRepository blockRepository = mock(BuddyBlockRepository.class);
	private final BuddyPublicProfileService service =
		new BuddyPublicProfileService(profileRepository, blockRepository);

	@Test
	void returnsAPublicProfileWithVisibleSocialLinksAndMessagePermission() {
		givenViewer(7L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(true, true, true))));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, false));
		when(profileRepository.findByUserId(7L))
			.thenReturn(Optional.of(profile(50L, 7L, viewerDraft(false))));

		BuddyProfileView result =
			service.getProfile("usr_viewer", 51L);

		assertEquals(51L, result.profileId());
		assertEquals("Target", result.nickname());
		assertEquals(
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@target")),
			result.socialLinks());
		assertTrue(result.canMessage());
		assertFalse(result.blockedByMe());
	}

	@Test
	void hidesPrivateSocialLinksAndRequiresAViewerProfileForMessaging() {
		givenViewer(7L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(true, false, true))));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, false));
		when(profileRepository.findByUserId(7L)).thenReturn(Optional.empty());

		BuddyProfileView result =
			service.getProfile("usr_viewer", 51L);

		assertTrue(result.socialLinks().isEmpty());
		assertFalse(result.canMessage());
	}

	@Test
	void returnsAProfileBlockedByTheViewerButDisablesMessaging() {
		givenViewer(7L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(true, true, true))));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(true, false));

		BuddyProfileView result =
			service.getProfile("usr_viewer", 51L);

		assertTrue(result.blockedByMe());
		assertFalse(result.canMessage());
	}

	@Test
	void hidesProfilesThatBlockedTheViewer() {
		givenViewer(7L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(true, true, true))));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, true));

		assertThrows(BuddyProfileNotFoundException.class,
			() -> service.getProfile("usr_viewer", 51L));
	}

	@Test
	void hidesAnotherUsersPrivateProfile() {
		givenViewer(7L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(false, true, true))));

		assertThrows(BuddyProfileNotFoundException.class,
			() -> service.getProfile("usr_viewer", 51L));
		verifyNoInteractions(blockRepository);
	}

	@Test
	void letsTheOwnerReadAPrivateProfileWithoutMessagingThemself() {
		givenViewer(8L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(false, false, true))));

		BuddyProfileView result =
			service.getProfile("usr_viewer", 51L);

		assertFalse(result.profilePublic());
		assertTrue(result.socialLinks().isEmpty());
		assertFalse(result.canMessage());
		assertFalse(result.blockedByMe());
		verifyNoInteractions(blockRepository);
	}

	@Test
	void disablesMessagingWhenTheTargetDoesNotAllowIt() {
		givenViewer(7L);
		when(profileRepository.findActiveById(51L))
			.thenReturn(Optional.of(profile(51L, 8L, targetDraft(true, true, false))));
		when(blockRepository.relationship(7L, 8L))
			.thenReturn(new BlockRelationship(false, false));
		when(profileRepository.findByUserId(7L))
			.thenReturn(Optional.of(profile(50L, 7L, viewerDraft(true))));

		BuddyProfileView result =
			service.getProfile("usr_viewer", 51L);

		assertFalse(result.canMessage());
	}

	@Test
	void rejectsMissingUsersProfilesAndNonPositiveIds() {
		when(profileRepository.findActiveUserId("usr_missing")).thenReturn(Optional.empty());
		assertThrows(BuddyUserUnavailableException.class,
			() -> service.getProfile("usr_missing", 51L));

		givenViewer(7L);
		when(profileRepository.findActiveById(999L)).thenReturn(Optional.empty());
		assertThrows(BuddyProfileNotFoundException.class,
			() -> service.getProfile("usr_viewer", 999L));

		assertThrows(IllegalArgumentException.class,
			() -> service.getProfile("usr_viewer", 0L));
	}

	private void givenViewer(long userId) {
		when(profileRepository.findActiveUserId("usr_viewer"))
			.thenReturn(Optional.of(userId));
	}

	private static BuddyProfileRecord profile(
		long profileId,
		long userId,
		BuddyProfileDraft draft
	) {
		return new BuddyProfileRecord(profileId, userId, draft, CREATED_AT, UPDATED_AT);
	}

	private static BuddyProfileDraft targetDraft(
		boolean profilePublic,
		boolean snsPublic,
		boolean allowsMessages
	) {
		return new BuddyProfileDraft(
			"https://cdn.example.com/target.jpg",
			"Target",
			"France",
			List.of(PlaceLanguage.EN, PlaceLanguage.KO),
			KoreanLevel.BEGINNER,
			"Local food fan",
			List.of(BuddyStyle.FOODIE),
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@target")),
			profilePublic,
			snsPublic,
			allowsMessages);
	}

	private static BuddyProfileDraft viewerDraft(boolean profilePublic) {
		return new BuddyProfileDraft(
			null,
			"Viewer",
			null,
			List.of(PlaceLanguage.EN),
			KoreanLevel.INTERMEDIATE,
			null,
			List.of(),
			List.of(),
			profilePublic,
			false,
			true);
	}
}
