package koready_backend.onboarding.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.onboarding.application.OnboardingService;

@RestController
@RequestMapping("/api/v1/users/me/onboarding")
public class OnboardingController {

	private final OnboardingService service;

	public OnboardingController(OnboardingService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<OnboardingDtos.ProgressResponse> getProgress(
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ONBOARDING_PROGRESS_OK",
			OnboardingDtos.from(service.getProgress(authentication.getName())),
			TraceIdFilter.current(request));
	}

	@PutMapping
	public ApiEnvelope<OnboardingDtos.CompletionResponse> complete(
		@RequestBody @Valid OnboardingDtos.CompletionRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ONBOARDING_COMPLETED",
			OnboardingDtos.from(service.complete(authentication.getName(), body.toCommand())),
			TraceIdFilter.current(request));
	}
}
