package koready_backend.buddy.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.buddy.application.BuddyProfileService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.port.ResponseLanguageResolver;

@RestController
@RequestMapping("/api/v1/users/me/buddy-profile")
public class BuddyProfileController {

	private final BuddyProfileService service;
	private final ResponseLanguageResolver languageResolver;

	public BuddyProfileController(
		BuddyProfileService service,
		ResponseLanguageResolver languageResolver
	) {
		this.service = service;
		this.languageResolver = languageResolver;
	}

	@GetMapping
	public ApiEnvelope<BuddyProfileDtos.MyBuddyProfileResponse> getMyProfile(
		Authentication authentication,
		@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BUDDY_PROFILE_OK",
			BuddyProfileDtos.from(service.getMyProfile(authentication.getName()),
				languageResolver.resolve(authentication.getName(), acceptLanguage)),
			TraceIdFilter.current(request));
	}

	@PutMapping
	public ApiEnvelope<BuddyProfileDtos.MyBuddyProfileData> upsertMyProfile(
		@RequestBody @Valid BuddyProfileDtos.BuddyProfileRequest body,
		Authentication authentication,
		@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BUDDY_PROFILE_SAVED",
			BuddyProfileDtos.fromMy(service.upsertMyProfile(
				authentication.getName(), body.toCommand()),
				languageResolver.resolve(authentication.getName(), acceptLanguage)),
			TraceIdFilter.current(request));
	}
}
