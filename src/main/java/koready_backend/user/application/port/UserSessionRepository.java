package koready_backend.user.application.port;

import java.time.Instant;
import java.util.Optional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.SignupStatus;

public interface UserSessionRepository {

	Optional<UserSessionRecord> find(String publicId, Instant asOf);

	record UserSessionRecord(
		long userId,
		String publicId,
		String email,
		String profileImageUrl,
		PlaceLanguage preferredLanguage,
		SignupStatus signupStatus,
		Long defaultLocationId,
		boolean onboardingCompleted,
		boolean buddyProfileExists,
		long unreadMessageCount,
		boolean termsNeedReAgreement
	) {
	}
}
