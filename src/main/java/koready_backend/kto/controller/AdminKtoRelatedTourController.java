package koready_backend.kto.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
import koready_backend.kto.application.KtoRelatedTourCurationService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/kto/related-tours")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminKtoRelatedTourController {

	private static final String WRITE_ROLES =
		"hasAnyRole('ADMIN', 'OPERATOR')";

	private final KtoRelatedTourCurationService service;

	public AdminKtoRelatedTourController(
		KtoRelatedTourCurationService service
	) {
		this.service = service;
	}

	@GetMapping
	@Operation(
		summary = "KTO 연관 관광지 수집 결과 조회",
		description = """
			KTO가 제공한 원본 관광지와 연관 관광지의 쌍, 추천 순위, 장소 연결 상태를 조회합니다.
			query에는 원본 또는 연관 관광지 이름을 넣을 수 있습니다.
			matchStatus는 UNMATCHED, AUTO_CONFIRMED, MANUAL_CONFIRMED 중 하나입니다.
			AUTO_CONFIRMED는 한국어 이름과 행정구역이 각각 하나의 KoReady 장소와 정확히 일치한 경우입니다.
			동명이거나 지역이 불분명한 항목은 UNMATCHED로 남으며 장소 상세 화면에는 노출되지 않습니다.
			목록의 id는 다음 확정·해제 API에서 사용하는 KTO 연관 관광지 레코드 ID입니다.
			""")
	public ApiEnvelope<KtoRelatedTourDtos.RelatedTourListResponse> list(
		@RequestParam(required = false) @Size(max = 100) String query,
		@RequestParam(required = false) @Size(max = 30)
		String matchStatus,
		@RequestParam(required = false) @Size(max = 30) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_RELATED_TOUR_LIST_OK",
			KtoRelatedTourDtos.from(
				service.list(
					query, matchStatus, cursor(cursor), size)),
			TraceIdFilter.current(request));
	}

	@PutMapping("/{recordId}/mapping")
	@PreAuthorize(WRITE_ROLES)
	@Operation(
		summary = "KTO 연관 관광지 장소 연결 확정",
		description = """
			자동으로 연결되지 않았거나 잘못 연결된 KTO 연관 관광지 레코드를 운영자가 직접 확정합니다.
			sourcePlaceId는 출발 관광지, relatedPlaceId는 함께 가기 좋은 관광지의 KoReady 장소 ID입니다.
			두 ID는 서로 달라야 하며 실제 장소로 존재해야 합니다.
			확정된 관계만 장소 상세 API의 relatedPlaces에 KTO 추천 순서대로 최대 3개 노출됩니다.
			reason에는 이름, 주소, 행정구역 등 확인 근거를 남깁니다.
			""")
	public ApiEnvelope<KtoRelatedTourDtos.RelatedTourResponse> confirm(
		@PathVariable @Positive long recordId,
		@RequestBody @Valid
		KtoRelatedTourDtos.ConfirmMappingRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_RELATED_TOUR_MAPPING_CONFIRMED",
			KtoRelatedTourDtos.from(service.confirmMapping(
				recordId, body.toCommand(), authentication.getName())),
			TraceIdFilter.current(request));
	}

	@DeleteMapping("/{recordId}/mapping")
	@PreAuthorize(WRITE_ROLES)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(
		summary = "KTO 연관 관광지 장소 연결 해제",
		description = """
			잘못 확정된 장소 관계를 해제해 장소 상세 화면에서 즉시 제외합니다.
			KTO 원본 레코드는 재검토와 다음 수집 비교를 위해 삭제하지 않습니다.
			reason에는 해제 사유를 기록하며 모든 변경은 관리자 감사 로그에 남습니다.
			""")
	public void remove(
		@PathVariable @Positive long recordId,
		@RequestBody @Valid
		KtoRelatedTourDtos.RemoveMappingRequest body,
		Authentication authentication
	) {
		service.removeMapping(
			recordId, body.reason(), authentication.getName());
	}

	private static long cursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0L;
		}
		try {
			long value = Long.parseLong(cursor);
			if (value < 0) {
				throw new IllegalArgumentException(
					"Related tour cursor is invalid");
			}
			return value;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
				"Related tour cursor is invalid");
		}
	}
}
