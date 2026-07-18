package koready_backend.horitip.controller;

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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.horitip.application.HoriTipService;
import koready_backend.horitip.domain.HoriTipStatus;

@Validated
@RestController
@RequestMapping("/api/v1/admin/hori-tips")
public class AdminHoriTipController {

	private static final String READ_ROLES = "hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')";
	private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'OPERATOR')";
	private static final Set<String> EDIT_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_OPERATOR");

	private final HoriTipService service;

	public AdminHoriTipController(HoriTipService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize(READ_ROLES)
	public ApiEnvelope<HoriTipDtos.AdminHoriTipListResponse> list(
		@RequestParam(required = false) HoriTipStatus status,
		@RequestParam(required = false) @Size(max = 80) String code,
		@RequestParam(required = false) @Positive Long destinationPlaceId,
		@RequestParam(required = false) @Size(max = 256) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ADMIN_HORI_TIP_LIST_OK",
			HoriTipDtos.from(service.list(
				status,
				code,
				destinationPlaceId,
				cursor,
				size,
				canEdit(authentication))),
			TraceIdFilter.current(request));
	}

	@PostMapping
	@PreAuthorize(WRITE_ROLES)
	public ResponseEntity<ApiEnvelope<HoriTipDtos.AdminHoriTipResponse>> create(
		@RequestBody @Valid HoriTipDtos.CreateHoriTipRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var result = service.create(
			body.toCommand(), authentication.getName(), canEdit(authentication));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
			"ADMIN_HORI_TIP_CREATED",
			HoriTipDtos.from(result),
			TraceIdFilter.current(request)));
	}

	@GetMapping("/{horiTipId}")
	@PreAuthorize(READ_ROLES)
	public ApiEnvelope<HoriTipDtos.AdminHoriTipResponse> get(
		@PathVariable @Positive long horiTipId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(service.get(horiTipId, canEdit(authentication)), request);
	}

	@PutMapping("/{horiTipId}")
	@PreAuthorize(WRITE_ROLES)
	public ApiEnvelope<HoriTipDtos.AdminHoriTipResponse> update(
		@PathVariable @Positive long horiTipId,
		@RequestBody @Valid HoriTipDtos.UpdateHoriTipRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(service.update(
			horiTipId,
			body.toCommand(),
			authentication.getName(),
			canEdit(authentication)), request);
	}

	@PutMapping("/{horiTipId}/status")
	@PreAuthorize(WRITE_ROLES)
	public ApiEnvelope<HoriTipDtos.AdminHoriTipResponse> changeStatus(
		@PathVariable @Positive long horiTipId,
		@RequestBody @Valid HoriTipDtos.UpdateHoriTipStatusRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return success(service.changeStatus(
			horiTipId,
			body.toCommand(),
			authentication.getName(),
			canEdit(authentication)), request);
	}

	private static boolean canEdit(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> EDIT_AUTHORITIES.contains(authority.getAuthority()));
	}

	private static ApiEnvelope<HoriTipDtos.AdminHoriTipResponse> success(
		HoriTipService.HoriTipView tip,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"ADMIN_HORI_TIP_OK",
			HoriTipDtos.from(tip),
			TraceIdFilter.current(request));
	}
}
