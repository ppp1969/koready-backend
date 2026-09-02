package koready_backend.user.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.application.port.UserSessionRepository;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

@Service
public class UserSessionService {

	private final UserSessionRepository repository;
	private final Clock clock;

	@Autowired
	public UserSessionService(UserSessionRepository repository) {
		this(repository, Clock.systemUTC());
	}

	UserSessionService(UserSessionRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public UserSession get(String publicId) {
		var row = repository.find(publicId, clock.instant())
			.orElseThrow(UserUnavailableException::new);
		return new UserSession(
			row.userId(), row.publicId(), row.email(), row.profileImageUrl(),
			row.preferredLanguage(), publicStatus(row.signupStatus()),
			row.signupStatus().nextStep(), row.defaultLocationId(),
			row.onboardingCompleted(), row.buddyProfileExists(),
			row.unreadMessageCount(), row.termsNeedReAgreement());
	}

	private static PublicSignupStatus publicStatus(SignupStatus status) {
		return switch (status) {
			case NEED_TERMS -> PublicSignupStatus.TERMS_REQUIRED;
			case NEED_LANGUAGE -> PublicSignupStatus.LANGUAGE_REQUIRED;
			case NEED_ONBOARDING -> PublicSignupStatus.ONBOARDING_REQUIRED;
			case COMPLETED -> PublicSignupStatus.ACTIVE;
		};
	}

	public enum PublicSignupStatus {
		TERMS_REQUIRED,
		LANGUAGE_REQUIRED,
		ONBOARDING_REQUIRED,
		ACTIVE
	}

	public record UserSession(
		long userId,
		String publicId,
		String email,
		String profileImageUrl,
		PlaceLanguage preferredLanguage,
		PublicSignupStatus signupStatus,
		NextStep nextStep,
		Long defaultLocationId,
		boolean onboardingCompleted,
		boolean buddyProfileExists,
		long unreadMessageCount,
		boolean termsNeedReAgreement
	) {
	}
}
