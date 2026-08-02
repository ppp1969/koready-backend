package koready_backend.onboarding.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.onboarding.application.CandidateSetService;
import koready_backend.place.application.port.ResponseLanguageResolver;

@RestController
@RequestMapping("/api/v1/onboarding/place-candidate-sets")
public class OnboardingCandidateSetController {

	private final CandidateSetService service;
	private final ResponseLanguageResolver languageResolver;

	public OnboardingCandidateSetController(
		CandidateSetService service,
		ResponseLanguageResolver languageResolver
	) {
		this.service = service;
		this.languageResolver = languageResolver;
	}

	@GetMapping("/current")
	public ApiEnvelope<CandidateSetDtos.CurrentCandidateSetResponse> getCurrent(
		@RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
		String acceptLanguage,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ONBOARDING_CANDIDATE_SET_OK",
			CandidateSetDtos.from(service.getCurrent(
				languageResolver.resolve(authentication.getName(), acceptLanguage))),
			TraceIdFilter.current(request));
	}
}
