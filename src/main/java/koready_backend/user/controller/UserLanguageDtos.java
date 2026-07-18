package koready_backend.user.controller;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.user.application.UserLanguageService;
import koready_backend.user.domain.NextStep;

final class UserLanguageDtos {

	private UserLanguageDtos() {
	}

	static LanguageResponse from(UserLanguageService.LanguageResult result) {
		return new LanguageResponse(
			result.language(), result.nextStep(), result.updatedAt());
	}

	record LanguageRequest(@NotNull PlaceLanguage language) {
	}

	record LanguageResponse(
		PlaceLanguage language,
		NextStep nextStep,
		Instant updatedAt
	) {
	}
}
