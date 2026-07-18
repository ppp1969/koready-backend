package koready_backend.onboarding.controller;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.onboarding.application.CandidateSetService;
import koready_backend.onboarding.domain.CandidateSetStatus;

@Validated
@RestController
@RequestMapping("/api/v1/admin/onboarding/place-candidate-sets")
public class AdminCandidateSetController {

	private static final String READ_ROLES = "hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')";
	private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'OPERATOR')";
	private static final Set<String> EDIT_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_OPERATOR");

	private final CandidateSetService service;

	public AdminCandidateSetController(CandidateSetService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize(READ_ROLES)
	public ApiEnvelope<CandidateSetDtos.AdminCandidateSetListResponse> list(
		@RequestParam(required = false) CandidateSetStatus status,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ADMIN_CANDIDATE_SET_LIST_OK",
			CandidateSetDtos.from(service.list(status, cursor, size)),
			TraceIdFilter.current(request));
	}

	@PostMapping
	@PreAuthorize(WRITE_ROLES)
	public ResponseEntity<ApiEnvelope<CandidateSetDtos.AdminCandidateSetResponse>> create(
		@RequestBody @Valid CandidateSetDtos.CreateCandidateSetRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var result = service.createDraft(
			body.toCommand(), authentication.getName(), canEdit(authentication));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
			"ADMIN_CANDIDATE_SET_CREATED",
			CandidateSetDtos.from(result),
			TraceIdFilter.current(request)));
	}

	@GetMapping("/{candidateSetId}")
	@PreAuthorize(READ_ROLES)
	public ApiEnvelope<CandidateSetDtos.AdminCandidateSetResponse> get(
		@PathVariable @NotBlank String candidateSetId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(
			service.getAdmin(candidateSetId, canEdit(authentication)),
			request);
	}

	@PutMapping("/{candidateSetId}")
	@PreAuthorize(WRITE_ROLES)
	public ApiEnvelope<CandidateSetDtos.AdminCandidateSetResponse> update(
		@PathVariable @NotBlank String candidateSetId,
		@RequestBody @Valid CandidateSetDtos.UpdateCandidateSetRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(
			service.updateDraft(
				candidateSetId,
				body.toCommand(),
				authentication.getName(),
				canEdit(authentication)),
			request);
	}

	@PostMapping("/{candidateSetId}/publish")
	@PreAuthorize(WRITE_ROLES)
	public ApiEnvelope<CandidateSetDtos.AdminCandidateSetResponse> publish(
		@PathVariable @NotBlank String candidateSetId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(
			service.publish(
				candidateSetId,
				authentication.getName(),
				canEdit(authentication)),
			request);
	}

	@PostMapping("/{candidateSetId}/archive")
	@PreAuthorize(WRITE_ROLES)
	public ApiEnvelope<CandidateSetDtos.AdminCandidateSetResponse> archive(
		@PathVariable @NotBlank String candidateSetId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(
			service.archive(
				candidateSetId,
				authentication.getName(),
				canEdit(authentication)),
			request);
	}

	private static boolean canEdit(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> EDIT_AUTHORITIES.contains(authority.getAuthority()));
	}

	private static ApiEnvelope<CandidateSetDtos.AdminCandidateSetResponse> success(
		CandidateSetService.AdminCandidateSet set,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ADMIN_CANDIDATE_SET_OK",
			CandidateSetDtos.from(set),
			TraceIdFilter.current(request));
	}
}
