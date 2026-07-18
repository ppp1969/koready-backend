package koready_backend.user.application.port;

import java.time.Instant;
import java.util.Optional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.SignupStatus;

public interface UserLanguageRepository {

	Optional<UserLanguageState> findByPublicIdForUpdate(String publicId);

	UserLanguageState update(
		long userId,
		PlaceLanguage language,
		SignupStatus signupStatus,
		Instant updatedAt
	);

	record UserLanguageState(
		long userId,
		PlaceLanguage language,
		SignupStatus signupStatus,
		Instant updatedAt
	) {
	}
}
