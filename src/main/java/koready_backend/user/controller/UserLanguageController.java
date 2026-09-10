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
import koready_backend.location.application.UserLocationService;

@RestController
@RequestMapping("/api/v1/users/me/language")
public class UserLanguageController {

	private final UserLanguageService service;
	private final UserLocationService locationService;

	public UserLanguageController(
		UserLanguageService service,
		UserLocationService locationService
	) {
		this.service = service;
		this.locationService = locationService;
	}

	@PatchMapping
	public ApiEnvelope<UserLanguageDtos.LanguageResponse> updateLanguage(
		@RequestBody @Valid UserLanguageDtos.LanguageRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var result = service.update(authentication.getName(), body.language());
		locationService.prepareLanguage(authentication.getName(), body.language());
		return ApiEnvelope.success(
			"USER_LANGUAGE_UPDATED",
			UserLanguageDtos.from(result),
			TraceIdFilter.current(request));
	}
}
