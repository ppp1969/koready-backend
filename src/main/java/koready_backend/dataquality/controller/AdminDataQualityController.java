package koready_backend.dataquality.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.dataquality.application.DataQualityAdminService;

@RestController
@RequestMapping("/api/v1/admin/data-quality")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminDataQualityController {

	private final DataQualityAdminService service;

	public AdminDataQualityController(DataQualityAdminService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	public ApiEnvelope<DataQualityDtos.DataQualityResponse> summary(
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"DATA_QUALITY_SUMMARY_OK",
			DataQualityDtos.from(service.summary()),
			TraceIdFilter.current(request));
	}
}
