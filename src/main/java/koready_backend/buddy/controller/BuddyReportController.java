package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.buddy.application.BuddyReportService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/reports")
public class BuddyReportController {

	private final BuddyReportService service;

	public BuddyReportController(BuddyReportService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiEnvelope<BuddyReportDtos.ReportResponse> create(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid BuddyReportDtos.ReportRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"REPORT_CREATED",
			BuddyReportDtos.ReportResponse.from(service.create(
				authentication.getName(), idempotencyKey, body.toCommand())),
			TraceIdFilter.current(request));
	}
}
