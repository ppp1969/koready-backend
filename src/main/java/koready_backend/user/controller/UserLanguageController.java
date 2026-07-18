package koready_backend.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.user.application.UserLanguageService;

@RestController
@RequestMapping("/api/v1/users/me/language")
public class UserLanguageController {

	private final UserLanguageService service;

	public UserLanguageController(UserLanguageService service) {
		this.service = service;
	}

	@PatchMapping
	public ApiEnvelope<UserLanguageDtos.LanguageResponse> updateLanguage(
		@RequestBody @Valid UserLanguageDtos.LanguageRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"USER_LANGUAGE_UPDATED",
			UserLanguageDtos.from(service.update(
				authentication.getName(), body.language())),
			TraceIdFilter.current(request));
	}
}
