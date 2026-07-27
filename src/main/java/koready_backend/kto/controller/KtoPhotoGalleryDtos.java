package koready_backend.kto.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.kto.application.KtoPhotoGalleryCurationService;

final class KtoPhotoGalleryDtos {

	private KtoPhotoGalleryDtos() {
	}

	static PhotoGalleryListResponse from(
		KtoPhotoGalleryCurationService.PhotoGalleryPage page
	) {
		return new PhotoGalleryListResponse(
			page.items().stream()
				.map(KtoPhotoGalleryDtos::from)
				.toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static PhotoGalleryResponse from(
		KtoPhotoGalleryCurationService.PhotoGalleryView image
	) {
		return new PhotoGalleryResponse(
			image.contentId(),
			image.contentTypeId(),
			image.title(),
			image.photographyLocation(),
			image.photographyMonth(),
			image.photographer(),
			image.searchKeyword(),
			image.imageUrl(),
			image.rightsStatus(),
			image.mappedPlaceId(),
			image.mappedPlaceTitleKo(),
			image.displayOrder(),
			image.approvedBySubject(),
			image.approvalReason(),
			image.approvedAt(),
			image.sourceCapturedAt());
	}

	record ApproveMappingRequest(
		@Positive long placeId,
		@Min(1) @Max(20) int displayOrder,
		@NotBlank @Size(max = 500) String reason
	) {
		KtoPhotoGalleryCurationService.ApproveMappingCommand toCommand() {
			return new KtoPhotoGalleryCurationService.ApproveMappingCommand(
				placeId, displayOrder, reason);
		}
	}

	record RemoveMappingRequest(
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record PhotoGalleryListResponse(
		List<PhotoGalleryResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record PhotoGalleryResponse(
		String contentId,
		String contentTypeId,
		String title,
		String photographyLocation,
		String photographyMonth,
		String photographer,
		String searchKeyword,
		String imageUrl,
		String rightsStatus,
		Long mappedPlaceId,
		String mappedPlaceTitleKo,
		Integer displayOrder,
		String approvedBySubject,
		String approvalReason,
		Instant approvedAt,
		Instant sourceCapturedAt
	) {
	}
}
