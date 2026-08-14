package koready_backend.editorial.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialCandidateStatusFilter;
import koready_backend.editorial.domain.EditorialCandidateRegionFilter;

@Validated
@RestController
@RequestMapping("/api/v1/admin/editorial")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEditorialController {

	private final EditorialService service;

	public AdminEditorialController(EditorialService service) {
		this.service = service;
	}

	@GetMapping("/candidates")
	@Operation(
		summary = "AI 장소 가공 후보 조회",
		description = "이미지, 분류와 신뢰 가능한 영문 제목이 있는 장소를 선별 조건으로 조회합니다.")
	public ApiEnvelope<EditorialDtos.CandidateListResponse> candidates(
		@RequestParam(required = false) @Size(max = 100) String query,
		@RequestParam(required = false) EditorialCandidateStatusFilter status,
		@RequestParam(required = false) EditorialCandidateRegionFilter region,
		@RequestParam(required = false) Boolean hasKoreanOverview,
		@RequestParam(required = false) Boolean queueEligible,
		@RequestParam(required = false) @Size(max = 30) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"EDITORIAL_CANDIDATE_LIST_OK",
			EditorialDtos.from(service.candidates(
				query, status, region, hasKoreanOverview, queueEligible,
				cursor(cursor), size)),
			TraceIdFilter.current(request));
	}

	@PostMapping("/places/{placeId}/queue")
	@Operation(
		summary = "장소 AI 가공 큐 등록",
		description = "PM이 선별한 장소를 HIGH 우선순위로 등록합니다. 같은 원문과 프롬프트는 중복 등록되지 않습니다.")
	public ApiEnvelope<EditorialDtos.QueueResponse> queue(
		@PathVariable @Positive long placeId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"EDITORIAL_JOB_QUEUED",
			EditorialDtos.from(service.enqueueByAdmin(placeId, authentication.getName())),
			TraceIdFilter.current(request));
	}

	@GetMapping("/candidates/{placeId}")
	@Operation(
		summary = "AI 장소 가공 후보 상세 조회",
		description = "큐 등록 없이 KTO 한국어 원문, 이미지, 분류와 현재 작업 상태를 확인합니다.")
	public ApiEnvelope<EditorialDtos.CandidateDetailResponse> candidate(
		@PathVariable @Positive long placeId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"EDITORIAL_CANDIDATE_DETAIL_OK",
			EditorialDtos.from(service.candidate(placeId)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/jobs")
	@Operation(summary = "AI 장소 가공 작업 조회")
	public ApiEnvelope<EditorialDtos.JobListResponse> jobs(
		@RequestParam(required = false) EditorialJobStatus status,
		@RequestParam(required = false) @Size(max = 30) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"EDITORIAL_JOB_LIST_OK",
			EditorialDtos.from(service.jobs(status, cursor(cursor), size)),
			TraceIdFilter.current(request));
	}

	private static long cursor(String value) {
		if (value == null || value.isBlank()) {
			return 0L;
		}
		try {
			long parsed = Long.parseLong(value);
			if (parsed < 0) {
				throw new IllegalArgumentException("Editorial cursor is invalid");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Editorial cursor is invalid");
		}
	}
}
