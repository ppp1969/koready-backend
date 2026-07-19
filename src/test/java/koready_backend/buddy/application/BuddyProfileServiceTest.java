package koready_backend.buddy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.BuddyProfileRepository.BuddyProfileRecord;
import koready_backend.buddy.domain.BuddyProfileDraft;
import koready_backend.buddy.domain.BuddySocialLink;
import koready_backend.buddy.domain.BuddyStyle;
import koready_backend.buddy.domain.KoreanLevel;
import koready_backend.buddy.domain.SocialLinkType;
import koready_backend.place.domain.PlaceLanguage;

class BuddyProfileServiceTest {

	private static final Instant CREATED_AT = Instant.parse("2026-07-18T03:00:00Z");
	private static final Instant NOW = Instant.parse("2026-07-19T03:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

	private final BuddyProfileRepository repository = mock(BuddyProfileRepository.class);
	private final BuddyProfileService service = new BuddyProfileService(repository, CLOCK);

	@Test
	void returnsAnExplicitAbsentStateWhenTheUserHasNoProfile() {
		when(repository.findActiveUserId("usr_new")).thenReturn(Optional.of(7L));
		when(repository.findByUserId(7L)).thenReturn(Optional.empty());

		BuddyProfileService.MyProfileResult result = service.getMyProfile("usr_new");

		assertFalse(result.exists());
		assertNull(result.profile());
	}

	@Test
	void returnsAllPrivateEditingValuesForTheOwnersProfile() {
		BuddyProfileDraft draft = new BuddyProfileDraft(
			"https://cdn.example.com/emma.jpg",
			"Emma",
			"France",
			List.of(PlaceLanguage.EN, PlaceLanguage.KO),
			KoreanLevel.BEGINNER,
			"Local food fan",
			List.of(BuddyStyle.FOODIE),
			List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@emma")),
			true,
			false,
			true);
		when(repository.findActiveUserId("usr_emma")).thenReturn(Optional.of(7L));
		when(repository.findByUserId(7L)).thenReturn(Optional.of(
			new BuddyProfileRecord(51L, 7L, draft, CREATED_AT, NOW)));

		BuddyProfileService.MyProfileResult result = service.getMyProfile("usr_emma");

		assertTrue(result.exists());
		assertEquals(List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "@emma")),
			result.profile().socialLinks());
		assertFalse(result.profile().snsPublic());
		assertFalse(result.profile().canMessage());
		assertFalse(result.profile().blockedByMe());
	}

	@Test
	void normalizesAndSavesAFullProfileReplacement() {
		when(repository.findActiveUserIdForUpdate("usr_emma"))
			.thenReturn(Optional.of(7L));
		when(repository.save(eq(7L), any(BuddyProfileDraft.class), eq(NOW)))
			.thenAnswer(invocation -> new BuddyProfileRecord(
				51L, 7L, invocation.getArgument(1), CREATED_AT, NOW));

		BuddyProfileService.BuddyProfileView result = service.upsertMyProfile(
			"usr_emma",
			new BuddyProfileService.UpsertCommand(
				null,
				"  Emma  ",
				"  France  ",
				List.of(PlaceLanguage.EN, PlaceLanguage.KO),
				KoreanLevel.INTERMEDIATE,
				"  Hello Korea  ",
				List.of(BuddyStyle.FOODIE, BuddyStyle.PHOTOGRAPHY),
				List.of(new BuddySocialLink(SocialLinkType.INSTAGRAM, "  @emma  ")),
				true,
				true,
				true));

		ArgumentCaptor<BuddyProfileDraft> draft =
			ArgumentCaptor.forClass(BuddyProfileDraft.class);
		verify(repository).save(eq(7L), draft.capture(), eq(NOW));
		assertEquals("Emma", draft.getValue().nickname());
		assertEquals("France", draft.getValue().nationality());
		assertEquals("Hello Korea", draft.getValue().bio());
		assertEquals("@emma", draft.getValue().socialLinks().getFirst().value());
		assertEquals(51L, result.profileId());
		assertFalse(result.canMessage());
	}

	@Test
	void rejectsDuplicateLanguagesAndBuddyStyles() {
		when(repository.findActiveUserIdForUpdate("usr_emma"))
			.thenReturn(Optional.of(7L));

		assertThrows(IllegalArgumentException.class, () -> service.upsertMyProfile(
			"usr_emma",
			command(
				List.of(PlaceLanguage.EN, PlaceLanguage.EN),
				List.of(BuddyStyle.FOODIE))));
		assertThrows(IllegalArgumentException.class, () -> service.upsertMyProfile(
			"usr_emma",
			command(
				List.of(PlaceLanguage.EN),
				List.of(BuddyStyle.FOODIE, BuddyStyle.FOODIE))));
	}

	@Test
	void rejectsMalformedOrNonHttpProfileImageUrls() {
		when(repository.findActiveUserIdForUpdate("usr_emma"))
			.thenReturn(Optional.of(7L));
		when(repository.save(eq(7L), any(BuddyProfileDraft.class), eq(NOW)))
			.thenAnswer(invocation -> new BuddyProfileRecord(
				51L, 7L, invocation.getArgument(1), CREATED_AT, NOW));

		assertThrows(IllegalArgumentException.class, () -> service.upsertMyProfile(
			"usr_emma",
			imageCommand("not-a-url")));
		assertThrows(IllegalArgumentException.class, () -> service.upsertMyProfile(
			"usr_emma",
			imageCommand("javascript:alert(1)")));
	}

	@Test
	void rejectsADeletedOrMissingAuthenticatedUser() {
		when(repository.findActiveUserId("usr_missing")).thenReturn(Optional.empty());
		when(repository.findActiveUserIdForUpdate("usr_missing"))
			.thenReturn(Optional.empty());

		assertThrows(BuddyUserUnavailableException.class,
			() -> service.getMyProfile("usr_missing"));
		assertThrows(BuddyUserUnavailableException.class,
			() -> service.upsertMyProfile("usr_missing",
				command(List.of(PlaceLanguage.EN), List.of())));
	}

	private static BuddyProfileService.UpsertCommand command(
		List<PlaceLanguage> languages,
		List<BuddyStyle> styles
	) {
		return new BuddyProfileService.UpsertCommand(
			null, "Emma", null, languages, KoreanLevel.BEGINNER, null,
			styles, List.of(), true, false, true);
	}

	private static BuddyProfileService.UpsertCommand imageCommand(String imageUrl) {
		return new BuddyProfileService.UpsertCommand(
			imageUrl, "Emma", null, List.of(PlaceLanguage.EN), KoreanLevel.BEGINNER,
			null, List.of(), List.of(), true, false, true);
	}
}
