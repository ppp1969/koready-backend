package koready_backend.onboarding.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import koready_backend.onboarding.application.CandidateSetService;
import koready_backend.onboarding.application.CandidateSetService.AdminCandidateItem;
import koready_backend.onboarding.application.CandidateSetService.AdminCandidateSet;
import koready_backend.onboarding.application.CandidateSetService.CandidateSetPage;
import koready_backend.onboarding.application.CandidateSetService.CandidateSetSummary;
import koready_backend.onboarding.application.CandidateSetService.CurrentCandidateItem;
import koready_backend.onboarding.application.CandidateSetService.CurrentCandidateSet;
import koready_backend.onboarding.domain.CandidateSetItemDraft;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

final class CandidateSetDtos {

	private CandidateSetDtos() {
	}

	static CurrentCandidateSetResponse from(CurrentCandidateSet set) {
		return new CurrentCandidateSetResponse(
			set.candidateSetId(),
			set.version(),
			set.status(),
			set.publishedAt(),
			set.minSelection(),
			set.maxSelection(),
			set.items().stream().map(CandidateSetDtos::from).toList());
	}

	static AdminCandidateSetResponse from(AdminCandidateSet set) {
		return new AdminCandidateSetResponse(
			set.candidateSetId(),
			set.title(),
			set.version(),
			set.status(),
			set.itemCount(),
			set.current(),
			set.publishedAt(),
			set.createdAt(),
			set.updatedAt(),
			set.editable(),
			set.items().stream().map(CandidateSetDtos::from).toList(),
			set.publishedByUserId(),
			set.archivedAt());
	}

	static AdminCandidateSetListResponse from(CandidateSetPage page) {
		return new AdminCandidateSetListResponse(
			page.items().stream().map(CandidateSetDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	private static CurrentCandidateItemResponse from(CurrentCandidateItem item) {
		return new CurrentCandidateItemResponse(
			item.placeId(),
			item.title(),
			item.imageUrl(),
			item.serviceRegionCode(),
			item.serviceRegionName(),
			item.travelStyle(),
			item.tags(),
			item.curatorMessage(),
			item.displayOrder());
	}

	private static AdminCandidateSetSummaryResponse from(CandidateSetSummary set) {
		return new AdminCandidateSetSummaryResponse(
			set.candidateSetId(),
			set.title(),
			set.version(),
			set.status(),
			set.itemCount(),
			set.current(),
			set.publishedAt(),
			set.createdAt(),
			set.updatedAt());
	}

	private static AdminCandidateSetItemResponse from(AdminCandidateItem item) {
		return new AdminCandidateSetItemResponse(
			item.placeId(),
			item.titleKo(),
			item.titleEn(),
			item.representativeImageId(),
			item.imageUrl(),
			item.displayOrder(),
			item.curatorMessageKo(),
			item.curatorMessageEn(),
			item.displayTags(),
			item.editorNote(),
			item.placeReady(),
			item.notReadyReasons());
	}

	record CreateCandidateSetRequest(
		@NotBlank @Size(max = 100) String title,
		@Size(max = 100) String copyFromSetId
	) {
		CandidateSetService.CreateCandidateSetCommand toCommand() {
			return new CandidateSetService.CreateCandidateSetCommand(title, copyFromSetId);
		}
	}

	record UpdateCandidateSetRequest(
		@NotBlank @Size(max = 100) String title,
		@NotNull @Size(max = 10) List<@Valid CandidateSetItemInput> items
	) {
		CandidateSetService.UpdateCandidateSetCommand toCommand() {
			return new CandidateSetService.UpdateCandidateSetCommand(
				title,
				items.stream().map(CandidateSetItemInput::toDraft).toList());
		}
	}

	record CandidateSetItemInput(
		@Positive long placeId,
		@Min(1) @Max(10) int displayOrder,
		@Positive Long representativeImageId,
		@NotBlank @Size(max = 160) String curatorMessageKo,
		@Size(max = 240) String curatorMessageEn,
		@NotNull @Size(max = 5) List<@NotBlank @Size(max = 30) String> displayTags,
		@Size(max = 500) String editorNote
	) {
		CandidateSetItemDraft toDraft() {
			return new CandidateSetItemDraft(
				placeId,
				displayOrder,
				representativeImageId,
				curatorMessageKo,
				curatorMessageEn,
				displayTags,
				editorNote);
		}
	}

	record CurrentCandidateSetResponse(
		String candidateSetId,
		int version,
		CandidateSetStatus status,
		Instant publishedAt,
		int minSelection,
		int maxSelection,
		List<CurrentCandidateItemResponse> items
	) {
	}

	record CurrentCandidateItemResponse(
		long placeId,
		String title,
		String imageUrl,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		TravelStyle travelStyle,
		List<String> tags,
		String curatorMessage,
		int displayOrder
	) {
	}

	record AdminCandidateSetListResponse(
		List<AdminCandidateSetSummaryResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record AdminCandidateSetSummaryResponse(
		String candidateSetId,
		String title,
		int version,
		CandidateSetStatus status,
		int itemCount,
		boolean current,
		Instant publishedAt,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	record AdminCandidateSetResponse(
		String candidateSetId,
		String title,
		int version,
		CandidateSetStatus status,
		int itemCount,
		boolean current,
		Instant publishedAt,
		Instant createdAt,
		Instant updatedAt,
		boolean editable,
		List<AdminCandidateSetItemResponse> items,
		Long publishedByUserId,
		Instant archivedAt
	) {
	}

	record AdminCandidateSetItemResponse(
		long placeId,
		String titleKo,
		String titleEn,
		Long representativeImageId,
		String imageUrl,
		int displayOrder,
		String curatorMessageKo,
		String curatorMessageEn,
		List<String> displayTags,
		String editorNote,
		boolean placeReady,
		List<String> notReadyReasons
	) {
	}
}
