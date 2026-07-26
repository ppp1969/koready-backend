package koready_backend.kto.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.kto.application.KtoDetailCoverageService;

@RestController
@RequestMapping("/api/v1/admin/kto")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminKtoDetailCoverageController {

	private final KtoDetailCoverageService service;

	public AdminKtoDetailCoverageController(KtoDetailCoverageService service) {
		this.service = service;
	}

	@GetMapping("/detail-coverage")
	public ApiEnvelope<KtoDetailCoverageDtos.CoverageResponse> coverage(
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_DETAIL_COVERAGE_OK",
			KtoDetailCoverageDtos.from(service.summary()),
			TraceIdFilter.current(request));
	}
}
