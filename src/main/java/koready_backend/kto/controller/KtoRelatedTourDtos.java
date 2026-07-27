package koready_backend.kto.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.kto.application.KtoRelatedTourCurationService;

final class KtoRelatedTourDtos {

	private KtoRelatedTourDtos() {
	}

	static RelatedTourListResponse from(
		KtoRelatedTourCurationService.RelatedTourPage page
	) {
		return new RelatedTourListResponse(
			page.items().stream()
				.map(KtoRelatedTourDtos::from)
				.toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static RelatedTourResponse from(
		KtoRelatedTourCurationService.RelatedTourView item
	) {
		return new RelatedTourResponse(
			item.id(),
			item.baseYearMonth(),
			item.sourceTourCode(),
			item.sourceName(),
			item.sourceRegionName(),
			item.sourceSignguName(),
			item.relatedTourCode(),
			item.relatedName(),
			item.relatedRegionName(),
			item.relatedSignguName(),
			item.categoryLarge(),
			item.categoryMedium(),
			item.categorySmall(),
			item.rank(),
			item.matchStatus(),
			item.sourcePlaceId(),
			item.sourcePlaceTitle(),
			item.relatedPlaceId(),
			item.relatedPlaceTitle(),
			item.confirmedBySubject(),
			item.confirmationReason(),
			item.confirmedAt(),
			item.sourceCapturedAt());
	}

	record ConfirmMappingRequest(
		@Positive long sourcePlaceId,
		@Positive long relatedPlaceId,
		@NotBlank @Size(max = 500) String reason
	) {
		KtoRelatedTourCurationService.ConfirmMappingCommand toCommand() {
			return new KtoRelatedTourCurationService.ConfirmMappingCommand(
				sourcePlaceId, relatedPlaceId, reason);
		}
	}

	record RemoveMappingRequest(
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record RelatedTourListResponse(
		List<RelatedTourResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record RelatedTourResponse(
		long id,
		String baseYearMonth,
		String sourceTourCode,
		String sourceName,
		String sourceRegionName,
		String sourceSignguName,
		String relatedTourCode,
		String relatedName,
		String relatedRegionName,
		String relatedSignguName,
		String categoryLarge,
		String categoryMedium,
		String categorySmall,
		int rank,
		String matchStatus,
		Long sourcePlaceId,
		String sourcePlaceTitle,
		Long relatedPlaceId,
		String relatedPlaceTitle,
		String confirmedBySubject,
		String confirmationReason,
		Instant confirmedAt,
		Instant sourceCapturedAt
	) {
	}
}
