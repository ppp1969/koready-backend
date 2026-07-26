package koready_backend.buddy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.ProfileOptionService;
import koready_backend.buddy.application.ProfileOptionService.ProfileOptions;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/profile-options")
public class ProfileOptionController {

	private final ProfileOptionService service;

	public ProfileOptionController(ProfileOptionService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<ProfileOptions> getOptions(HttpServletRequest request) {
		return ApiEnvelope.success(
			"PROFILE_OPTIONS_OK",
			service.getOptions(),
			TraceIdFilter.current(request));
	}
}
