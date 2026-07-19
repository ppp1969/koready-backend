package koready_backend.buddy.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.BuddyBlockService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/users/me/blocked-profiles")
public class BuddySafetyController {

	private final BuddyBlockService service;

	public BuddySafetyController(BuddyBlockService service) {
		this.service = service;
	}

	@PutMapping("/{profileId}")
	public ApiEnvelope<BlockProfileResponse> block(
		@PathVariable long profileId,
		Authentication authentication,
		HttpServletRequest request
	) {
		BuddyBlockService.BlockResult result = service.block(
			authentication.getName(), profileId);
		return ApiEnvelope.success(
			"BUDDY_PROFILE_BLOCKED",
			new BlockProfileResponse(result.profileId(), true, result.blockedAt()),
			TraceIdFilter.current(request));
	}

	@DeleteMapping("/{profileId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unblock(
		@PathVariable long profileId,
		Authentication authentication
	) {
		service.unblock(authentication.getName(), profileId);
	}

	record BlockProfileResponse(
		long profileId,
		boolean blocked,
		Instant blockedAt
	) {
	}
}
