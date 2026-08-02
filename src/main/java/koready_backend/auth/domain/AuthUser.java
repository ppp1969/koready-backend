package koready_backend.auth.domain;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.SignupStatus;

public record AuthUser(
	long id,
	String publicId,
	String email,
	UserRole role,
	PlaceLanguage preferredLanguage,
	SignupStatus signupStatus,
	String profileImageUrl
) {
}
