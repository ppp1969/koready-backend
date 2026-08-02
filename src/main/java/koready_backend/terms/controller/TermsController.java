package koready_backend.terms.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.terms.application.TermsService;

@RestController
public class TermsController {

	private final TermsService service;

	public TermsController(TermsService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/terms/required")
	public ApiEnvelope<TermsDtos.RequiredTermsResponse> getRequiredTerms(
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"REQUIRED_TERMS_RETRIEVED",
			TermsDtos.from(service.getRequiredTerms(authentication.getName())),
			TraceIdFilter.current(request));
	}

	@PutMapping("/api/v1/users/me/term-agreements")
	public ApiEnvelope<TermsDtos.TermAgreementResponse> updateAgreements(
		@RequestBody @Valid TermsDtos.AgreementRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"TERM_AGREEMENTS_UPDATED",
			TermsDtos.from(service.updateAgreements(
				authentication.getName(), body.toCommands())),
			TraceIdFilter.current(request));
	}
}
