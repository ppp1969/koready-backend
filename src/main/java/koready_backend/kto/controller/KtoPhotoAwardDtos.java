package koready_backend.kto.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.kto.application.KtoPhotoAwardCurationService;

final class KtoPhotoAwardDtos {

	private KtoPhotoAwardDtos() {
	}

	static PhotoAwardListResponse from(
		KtoPhotoAwardCurationService.PhotoAwardPage page
	) {
		return new PhotoAwardListResponse(
			page.items().stream().map(KtoPhotoAwardDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static PhotoAwardResponse from(
		KtoPhotoAwardCurationService.PhotoAwardView award
	) {
		return new PhotoAwardResponse(
			award.contentId(),
			award.titleKo(),
			award.filmLocationKo(),
			award.keywordKo(),
			award.titleEn(),
			award.filmLocationEn(),
			award.keywordEn(),
			award.originalImageUrl(),
			award.thumbnailImageUrl(),
			award.copyrightType(),
			award.mappedPlaceId(),
			award.mappedPlaceTitleKo(),
			award.displayOrder(),
			award.approvedBySubject(),
			award.approvalReason(),
			award.approvedAt(),
			award.sourceCapturedAt());
	}

	record ApproveMappingRequest(
		@Positive long placeId,
		@Min(1) @Max(20) int displayOrder,
		@NotBlank @Size(max = 500) String reason
	) {
		KtoPhotoAwardCurationService.ApproveMappingCommand toCommand() {
			return new KtoPhotoAwardCurationService.ApproveMappingCommand(
				placeId, displayOrder, reason);
		}
	}

	record RemoveMappingRequest(
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record PhotoAwardListResponse(
		List<PhotoAwardResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record PhotoAwardResponse(
		String contentId,
		String titleKo,
		String filmLocationKo,
		String keywordKo,
		String titleEn,
		String filmLocationEn,
		String keywordEn,
		String originalImageUrl,
		String thumbnailImageUrl,
		String copyrightType,
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
