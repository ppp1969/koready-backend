package koready_backend.kto.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.kto.application.KtoEnglishQualityCoverageService;

@RestController
@RequestMapping("/api/v1/admin/kto/english-quality-coverage")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminKtoEnglishQualityCoverageController {

	private final KtoEnglishQualityCoverageService service;

	public AdminKtoEnglishQualityCoverageController(
		KtoEnglishQualityCoverageService service
	) {
		this.service = service;
	}

	@GetMapping
	@Operation(
		summary = "KTO 영문 원본 품질 판정 진행률 조회",
		description = "최신 영문 원본의 품질 판정 완료·미완료와 품질별 건수를 DB에서 집계합니다.")
	public ApiEnvelope<KtoEnglishQualityCoverageDtos.CoverageResponse> get(
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_ENGLISH_QUALITY_COVERAGE_OK",
			KtoEnglishQualityCoverageDtos.from(service.coverage()),
			TraceIdFilter.current(request));
	}
}
