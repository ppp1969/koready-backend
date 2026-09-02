package koready_backend.user.controller;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.UserSessionService;
import koready_backend.user.domain.NextStep;

final class UserSessionDtos {

	private UserSessionDtos() {
	}

	static MyUserResponse from(UserSessionService.UserSession session) {
		return new MyUserResponse(
			new UserSummary(
				session.userId(), session.publicId(), session.email(),
				session.profileImageUrl(), session.preferredLanguage()),
			session.signupStatus(), session.nextStep(), session.defaultLocationId(),
			session.onboardingCompleted(), session.buddyProfileExists(),
			session.unreadMessageCount(), session.termsNeedReAgreement());
	}

	record MyUserResponse(
		UserSummary user,
		UserSessionService.PublicSignupStatus signupStatus,
		NextStep nextStep,
		Long defaultLocationId,
		boolean onboardingCompleted,
		boolean buddyProfileExists,
		long unreadMessageCount,
		boolean termsNeedReAgreement
	) {
	}

	record UserSummary(
		long userId,
		String publicId,
		String email,
		String profileImageUrl,
		PlaceLanguage preferredLanguage
	) {
	}
}
