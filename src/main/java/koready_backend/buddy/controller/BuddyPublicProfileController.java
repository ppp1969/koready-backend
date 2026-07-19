package koready_backend.buddy.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.BuddyPublicProfileService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/buddy-profiles")
public class BuddyPublicProfileController {

	private final BuddyPublicProfileService service;

	public BuddyPublicProfileController(BuddyPublicProfileService service) {
		this.service = service;
	}

	@GetMapping("/{profileId}")
	public ApiEnvelope<BuddyProfileDtos.BuddyProfileResponse> getProfile(
		@PathVariable long profileId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BUDDY_PROFILE_OK",
			BuddyProfileDtos.from(service.getProfile(authentication.getName(), profileId)),
			TraceIdFilter.current(request));
	}
}
