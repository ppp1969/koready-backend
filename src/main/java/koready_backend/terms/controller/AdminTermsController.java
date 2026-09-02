package koready_backend.terms.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.terms.application.AdminTermsService;

@RestController
@Validated
@RequestMapping("/api/v1/admin/terms")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTermsController {
	private final AdminTermsService service;
	public AdminTermsController(AdminTermsService service) { this.service = service; }

	@GetMapping
	@Operation(summary="약관과 전체 버전 조회", description="관리자 화면에서 약관 종류, 노출 순서, 활성화 여부와 초안·게시·철회 버전을 한 번에 조회합니다.")
	public ApiEnvelope<?> list(HttpServletRequest request) { return ok("ADMIN_TERMS_OK", AdminTermsDtos.from(service.list()), request); }

	@PostMapping
	@Operation(summary="약관 종류 생성", description="예: TERMS_OF_SERVICE, PRIVACY_POLICY, AGE_OVER_14. 실제 본문은 버전으로 추가합니다.")
	public ApiEnvelope<?> create(@Valid @RequestBody AdminTermsDtos.CreateDefinitionRequest body, HttpServletRequest request) {
		return ok("ADMIN_TERM_CREATED", AdminTermsDtos.from(service.createDefinition(body.code(), body.displayOrder(), body.enabled())), request);
	}

	@PatchMapping("/{termId}")
	@Operation(summary="약관 종류 설정 변경", description="목록 노출 순서와 활성화 여부를 변경합니다. 비활성화하면 가입 화면의 현재 약관에서 제외됩니다.")
	public ApiEnvelope<?> update(@PathVariable @Positive long termId, @Valid @RequestBody AdminTermsDtos.UpdateDefinitionRequest body, HttpServletRequest request) {
		return ok("ADMIN_TERM_UPDATED", AdminTermsDtos.from(service.updateDefinition(termId, body.displayOrder(), body.enabled())), request);
	}

	@PostMapping("/{termId}/versions")
	@Operation(summary="약관 버전 초안 생성", description="본문 URL 없이도 저장할 수 있습니다. 필수 여부와 시행일은 버전마다 관리합니다.")
	public ApiEnvelope<?> createVersion(@PathVariable @Positive long termId, @Valid @RequestBody AdminTermsDtos.VersionRequest body, HttpServletRequest request) {
		return ok("ADMIN_TERM_VERSION_CREATED", AdminTermsDtos.from(service.createVersion(termId, body.command())), request);
	}

	@PutMapping("/{termId}/versions/{versionId}")
	@Operation(summary="약관 버전 초안 수정", description="게시 전 초안만 수정할 수 있습니다. 게시본 변경은 새 버전을 생성해야 합니다.")
	public ApiEnvelope<?> updateVersion(@PathVariable @Positive long termId, @PathVariable @Positive long versionId,
		@Valid @RequestBody AdminTermsDtos.VersionRequest body, HttpServletRequest request) {
		return ok("ADMIN_TERM_VERSION_UPDATED", AdminTermsDtos.from(service.updateDraft(termId, versionId, body.command())), request);
	}

	@PostMapping("/{termId}/versions/{versionId}/publish")
	@Operation(summary="약관 버전 게시", description="본문 URL이 있는 초안만 게시합니다. 시행일이 되면 사용자 약관 조회에 자동 반영됩니다.")
	public ApiEnvelope<?> publish(@PathVariable @Positive long termId, @PathVariable @Positive long versionId, HttpServletRequest request) {
		return ok("ADMIN_TERM_VERSION_PUBLISHED", AdminTermsDtos.from(service.publish(termId, versionId)), request);
	}

	@PostMapping("/{termId}/versions/{versionId}/withdraw")
	@Operation(summary="약관 버전 철회", description="게시된 버전을 더 이상 신규 동의 대상으로 제공하지 않습니다.")
	public ApiEnvelope<?> withdraw(@PathVariable @Positive long termId, @PathVariable @Positive long versionId, HttpServletRequest request) {
		return ok("ADMIN_TERM_VERSION_WITHDRAWN", AdminTermsDtos.from(service.withdraw(termId, versionId)), request);
	}

	private static ApiEnvelope<Object> ok(String code, Object data, HttpServletRequest request) {
		return ApiEnvelope.success(code, data, TraceIdFilter.current(request));
	}
}
