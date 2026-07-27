package koready_backend.kto.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.kto.application.KtoEnglishReviewService;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;

@Validated
@RestController
@RequestMapping("/api/v1/admin/kto/english-match-reviews")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminKtoEnglishReviewController {

	private final KtoEnglishReviewService service;

	public AdminKtoEnglishReviewController(KtoEnglishReviewService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(
		summary = "KTO 영문 매칭 검토 목록",
		description = """
			기본 호출은 아직 결정하지 않은 REVIEW_REQUIRED와 UNMATCHED 항목을 최신순으로 반환합니다.
			REVIEW_REQUIRED에는 이미지 경로 또는 좌표·콘텐츠 유형 근거로 찾은 국문 장소 후보가 있고,
			UNMATCHED에는 현재 matcher 근거 후보가 없습니다. status를 지정하면 확정·거절 이력도
			별도로 조회할 수 있습니다. 다음 페이지는 응답의 nextCursor를 그대로 전달합니다.
			"""
	)
	public ApiEnvelope<KtoEnglishReviewDtos.ReviewListResponse> list(
		@RequestParam(required = false) KtoEnglishReviewStatus status,
		@RequestParam(required = false) KtoEnglishSourceQuality quality,
		@RequestParam(required = false) @Size(max = 100) String search,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		var page = service.list(new KtoEnglishReviewService.ReviewQuery(
			status, quality, search, cursor, size));
		return ApiEnvelope.success(
			"KTO_ENGLISH_REVIEW_LIST_OK",
			KtoEnglishReviewDtos.from(page),
			TraceIdFilter.current(request));
	}

	@GetMapping("/{sourceRecordId}")
	@Operation(
		summary = "KTO 영문 매칭 검토 상세",
		description = """
			S3 원본 스냅샷의 영문 제목·주소·이미지·좌표와 matcher가 제시한 국문 장소 후보,
			후보별 근거, 이전 운영 결정 이력을 함께 반환합니다. 확정 화면은 이 응답의
			candidates 안에 있는 placeId만 선택해야 합니다.
			"""
	)
	public ApiEnvelope<KtoEnglishReviewDtos.ReviewDetailResponse> get(
		@PathVariable @Positive long sourceRecordId,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_ENGLISH_REVIEW_OK",
			KtoEnglishReviewDtos.from(service.get(sourceRecordId)),
			TraceIdFilter.current(request));
	}

	@PutMapping("/{sourceRecordId}/decision")
	@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
	@Operation(
		summary = "KTO 영문 매칭 확정 또는 거절",
		description = """
			MANUAL_CONFIRMED는 상세 응답 candidates에 있는 placeId만 허용합니다.
			REJECTED는 selectedPlaceId를 보내지 않습니다. expectedVersion에는 상세 응답의
			decisionVersion을 넣어야 하며, 다른 운영자가 먼저 수정했다면 409를 반환합니다.
			기존 MANUAL_EDITED 영문 문구는 보호되고 모든 결정은 작업자·사유·시각과 함께 기록됩니다.
			"""
	)
	public ApiEnvelope<KtoEnglishReviewDtos.DecisionResponse> decide(
		@PathVariable @Positive long sourceRecordId,
		@RequestBody @Valid KtoEnglishReviewDtos.DecisionRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var decided = service.decide(
			sourceRecordId,
			new KtoEnglishReviewService.ReviewDecisionCommand(
				body.decision(),
				body.selectedPlaceId(),
				body.expectedVersion(),
				authentication.getName(),
				body.reason()));
		return ApiEnvelope.success(
			"KTO_ENGLISH_REVIEW_DECIDED",
			KtoEnglishReviewDtos.from(decided),
			TraceIdFilter.current(request));
	}
}
