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
import jakarta.validation.constraints.Size;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.kto.application.KtoPhotoAwardCurationService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/kto/photo-awards")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminKtoPhotoAwardController {

	private static final String WRITE_ROLES =
		"hasAnyRole('ADMIN', 'OPERATOR')";

	private final KtoPhotoAwardCurationService service;

	public AdminKtoPhotoAwardController(
		KtoPhotoAwardCurationService service
	) {
		this.service = service;
	}

	@GetMapping
	@Operation(
		summary = "KTO 사진공모전 수상작 후보 조회",
		description = """
			수집된 사진공모전 수상작과 현재 장소 연결 상태를 조회합니다.
			query는 한·영 제목과 촬영 장소를 검색하고, mapped=true/false로
			연결 완료 여부를 나눌 수 있습니다. 수상작 contentId는 관광지
			contentid와 같은 값이 아니므로 이 목록만 보고 자동 연결하지 않습니다.
			nextCursor가 있으면 다음 요청의 cursor에 그대로 전달합니다.
			""")
	public ApiEnvelope<KtoPhotoAwardDtos.PhotoAwardListResponse> list(
		@RequestParam(required = false) @Size(max = 100) String query,
		@RequestParam(required = false) Boolean mapped,
		@RequestParam(required = false) @Size(max = 30) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_PHOTO_AWARD_LIST_OK",
			KtoPhotoAwardDtos.from(service.list(
				query, mapped, cursor(cursor), size)),
			TraceIdFilter.current(request));
	}

	@PutMapping("/{contentId}/mapping")
	@PreAuthorize(WRITE_ROLES)
	@Operation(
		summary = "사진공모전 수상작의 장소 연결 승인",
		description = """
			운영자가 사진 제목, 촬영 장소와 원본 이미지를 직접 확인한 뒤
			수상작을 KoReady 장소에 연결합니다. 승인된 이미지만 장소 상세의
			KTO_PHOTO_AWARD 출처로 저장되며 가장 먼저 노출됩니다.
			displayOrder는 같은 장소에 여러 수상작이 있을 때의 순서입니다.
			제목 유사도만으로 자동 승인하는 기능은 제공하지 않습니다.
			""")
	public ApiEnvelope<KtoPhotoAwardDtos.PhotoAwardResponse> approve(
		@PathVariable @Size(max = 100) String contentId,
		@RequestBody @Valid KtoPhotoAwardDtos.ApproveMappingRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_PHOTO_AWARD_MAPPING_APPROVED",
			KtoPhotoAwardDtos.from(service.approveMapping(
				contentId, body.toCommand(), authentication.getName())),
			TraceIdFilter.current(request));
	}

	@DeleteMapping("/{contentId}/mapping")
	@PreAuthorize(WRITE_ROLES)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(
		summary = "사진공모전 수상작 장소 연결 해제",
		description = """
			잘못 승인된 장소 연결과 해당 KTO_PHOTO_AWARD 이미지를 함께
			제거합니다. 수상작 원본 후보 데이터는 삭제하지 않아 다른 장소로
			다시 검토할 수 있고, 해제 사유와 작업자는 감사 기록에 남습니다.
			""")
	public void remove(
		@PathVariable @Size(max = 100) String contentId,
		@RequestBody @Valid KtoPhotoAwardDtos.RemoveMappingRequest body,
		Authentication authentication
	) {
		service.removeMapping(
			contentId, body.reason(), authentication.getName());
	}

	private static long cursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0L;
		}
		try {
			long value = Long.parseLong(cursor);
			if (value < 0) {
				throw new IllegalArgumentException(
					"Photo award cursor is invalid");
			}
			return value;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
				"Photo award cursor is invalid");
		}
	}
}
