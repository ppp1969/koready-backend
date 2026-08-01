package koready_backend.auth.controller;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import koready_backend.auth.application.GoogleAuthService;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.domain.NextStep;

final class AuthDtos {

	private AuthDtos() {
	}

	static TokenResponse from(GoogleAuthService.AuthResult result) {
		return new TokenResponse(
			"Bearer",
			result.accessToken(),
			result.refreshToken(),
			result.accessTokenExpiresAt(),
			result.refreshTokenExpiresAt(),
			new UserSummary(
				result.user().id(),
				result.user().publicId(),
				result.user().email(),
				result.user().profileImageUrl(),
				result.user().preferredLanguage()),
			result.nextStep());
	}

	record GoogleLoginRequest(
		@NotBlank @Size(max = 8192) String idToken,
		@NotBlank @Size(max = 100) String deviceId
	) {
	}

	record RefreshTokenRequest(
		@NotBlank @Size(max = 200) String refreshToken,
		@NotBlank @Size(max = 100) String deviceId
	) {
	}

	record TokenResponse(
		String tokenType,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt,
		UserSummary user,
		NextStep nextStep
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
