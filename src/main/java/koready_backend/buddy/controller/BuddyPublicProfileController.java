package koready_backend.buddy.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.BuddyPublicProfileService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.port.ResponseLanguageResolver;

@RestController
@RequestMapping("/api/v1/buddy-profiles")
public class BuddyPublicProfileController {

	private final BuddyPublicProfileService service;
	private final ResponseLanguageResolver languageResolver;

	public BuddyPublicProfileController(
		BuddyPublicProfileService service,
		ResponseLanguageResolver languageResolver
	) {
		this.service = service;
		this.languageResolver = languageResolver;
	}

	@GetMapping("/{profileId}")
	public ApiEnvelope<BuddyProfileDtos.BuddyProfileResponse> getProfile(
		@PathVariable long profileId,
		Authentication authentication,
		@RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BUDDY_PROFILE_OK",
			BuddyProfileDtos.from(service.getProfile(authentication.getName(), profileId),
				languageResolver.resolve(authentication.getName(), acceptLanguage)),
			TraceIdFilter.current(request));
	}
}
