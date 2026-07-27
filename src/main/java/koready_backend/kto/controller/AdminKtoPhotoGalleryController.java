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
import koready_backend.kto.application.KtoPhotoGalleryCurationService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/kto/photo-gallery")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
public class AdminKtoPhotoGalleryController {

	private static final String WRITE_ROLES =
		"hasAnyRole('ADMIN', 'OPERATOR')";

	private final KtoPhotoGalleryCurationService service;

	public AdminKtoPhotoGalleryController(
		KtoPhotoGalleryCurationService service
	) {
		this.service = service;
	}

	@GetMapping
	@Operation(
		summary = "KTO 관광사진 후보 조회",
		description = """
			수집된 KTO 관광사진과 현재 장소 연결 상태를 조회합니다.
			query로 제목·촬영지를 검색하고 mapped로 연결 여부를 필터링합니다.
			관광사진 ID는 TourAPI 장소 contentId가 아니므로 자동 연결하지 않습니다.
			rightsStatus가 REQUIRES_REVIEW인 사진은 운영자가 장소와 이용 가능성을
			확인한 뒤에만 매핑 승인해야 합니다.
			""")
	public ApiEnvelope<KtoPhotoGalleryDtos.PhotoGalleryListResponse> list(
		@RequestParam(required = false) @Size(max = 100) String query,
		@RequestParam(required = false) Boolean mapped,
		@RequestParam(required = false) @Size(max = 30) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_PHOTO_GALLERY_LIST_OK",
			KtoPhotoGalleryDtos.from(
				service.list(query, mapped, cursor(cursor), size)),
			TraceIdFilter.current(request));
	}

	@PutMapping("/{contentId}/mapping")
	@PreAuthorize(WRITE_ROLES)
	@Operation(
		summary = "KTO 관광사진 장소 연결 승인",
		description = """
			운영자가 사진, 촬영 장소와 이용 가능성을 확인한 뒤 내부 장소에 연결합니다.
			승인된 사진만 KTO_PHOTO_GALLERY 출처와 우선순위 250으로 장소 상세
			이미지에 반영됩니다. 사진공모전 수상작 300보다 낮고 KTO 기본 이미지
			200보다 높은 순서입니다. reason에는 장소와 이용권 검토 근거를 남깁니다.
			""")
	public ApiEnvelope<KtoPhotoGalleryDtos.PhotoGalleryResponse> approve(
		@PathVariable @Size(max = 100) String contentId,
		@RequestBody @Valid KtoPhotoGalleryDtos.ApproveMappingRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"KTO_PHOTO_GALLERY_MAPPING_APPROVED",
			KtoPhotoGalleryDtos.from(service.approveMapping(
				contentId, body.toCommand(), authentication.getName())),
			TraceIdFilter.current(request));
	}

	@DeleteMapping("/{contentId}/mapping")
	@PreAuthorize(WRITE_ROLES)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(
		summary = "KTO 관광사진 장소 연결 해제",
		description = """
			잘못 승인된 장소 연결과 공개 장소 이미지 연결을 제거합니다.
			수집된 KTO 관광사진 원천 메타데이터는 다음 검수를 위해 유지합니다.
			""")
	public void remove(
		@PathVariable @Size(max = 100) String contentId,
		@RequestBody @Valid KtoPhotoGalleryDtos.RemoveMappingRequest body,
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
					"Photo gallery cursor is invalid");
			}
			return value;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
				"Photo gallery cursor is invalid");
		}
	}
}
