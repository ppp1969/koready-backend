package koready_backend.buddy.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.buddy.application.BuddyProfileService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/users/me/buddy-profile")
public class BuddyProfileController {

	private final BuddyProfileService service;

	public BuddyProfileController(BuddyProfileService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<BuddyProfileDtos.MyBuddyProfileResponse> getMyProfile(
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BUDDY_PROFILE_OK",
			BuddyProfileDtos.from(service.getMyProfile(authentication.getName())),
			TraceIdFilter.current(request));
	}

	@PutMapping
	public ApiEnvelope<BuddyProfileDtos.BuddyProfileResponse> upsertMyProfile(
		@RequestBody @Valid BuddyProfileDtos.BuddyProfileRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"BUDDY_PROFILE_SAVED",
			BuddyProfileDtos.from(service.upsertMyProfile(
				authentication.getName(), body.toCommand())),
			TraceIdFilter.current(request));
	}
}
