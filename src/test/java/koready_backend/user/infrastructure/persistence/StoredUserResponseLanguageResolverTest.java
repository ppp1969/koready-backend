package koready_backend.user.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.port.UserLanguageRepository;
import koready_backend.user.application.port.UserLanguageRepository.UserLanguageState;
import koready_backend.user.domain.SignupStatus;

class StoredUserResponseLanguageResolverTest {

	private final UserLanguageRepository repository =
		mock(UserLanguageRepository.class);
	private final StoredUserResponseLanguageResolver resolver =
		new StoredUserResponseLanguageResolver(repository);

	@Test
	void usesTheStoredProfileLanguageForAnAuthenticatedUser() {
		when(repository.findByPublicId("usr_language"))
			.thenReturn(Optional.of(new UserLanguageState(
				1L,
				PlaceLanguage.EN,
				SignupStatus.COMPLETED,
				Instant.parse("2026-08-02T00:00:00Z"))));

		assertEquals(
			PlaceLanguage.EN,
			resolver.resolve("usr_language", "ko-KR"));
	}

	@Test
	void fallsBackToTheRequestLanguageForPublicOrMissingUsers() {
		when(repository.findByPublicId("usr_missing")).thenReturn(Optional.empty());

		assertEquals(PlaceLanguage.EN, resolver.resolve(null, "en-US"));
		assertEquals(PlaceLanguage.EN, resolver.resolve("usr_missing", "en-US"));
		assertEquals(PlaceLanguage.KO, resolver.resolve(null, null));
	}
}
