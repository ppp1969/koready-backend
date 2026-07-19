package koready_backend.evidence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.evidence.application.EvidenceBundleService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/evidence-bundles")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminEvidenceBundleController {

	private final EvidenceBundleService service;

	public AdminEvidenceBundleController(EvidenceBundleService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiEnvelope<EvidenceBundleService.BundleView>> create(
		@RequestBody @Valid EvidenceBundleDtos.CreateRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiEnvelope.success(
			"EVIDENCE_BUNDLE_ACCEPTED", service.create(body.toCommand(), authentication.getName()),
			TraceIdFilter.current(request)));
	}

	@GetMapping
	public ApiEnvelope<EvidenceBundleService.BundlePage> list(
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success("EVIDENCE_BUNDLE_LIST_OK", service.list(cursor, size),
			TraceIdFilter.current(request));
	}

	@GetMapping("/{bundleId}")
	public ApiEnvelope<EvidenceBundleService.BundleView> get(
		@PathVariable @Pattern(regexp = "evidence_[a-zA-Z0-9]{8,64}") String bundleId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success("EVIDENCE_BUNDLE_OK", service.get(bundleId),
			TraceIdFilter.current(request));
	}

	@PostMapping("/{bundleId}/download-url")
	public ApiEnvelope<EvidenceBundleService.DownloadView> createDownloadUrl(
		@PathVariable @Pattern(regexp = "evidence_[a-zA-Z0-9]{8,64}") String bundleId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success("EVIDENCE_BUNDLE_DOWNLOAD_URL_ISSUED",
			service.createDownloadUrl(bundleId, authentication.getName()), TraceIdFilter.current(request));
	}
}
