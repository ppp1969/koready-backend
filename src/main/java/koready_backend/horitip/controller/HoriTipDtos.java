package koready_backend.horitip.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import koready_backend.horitip.application.HoriTipService;
import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipPlacement;
import koready_backend.horitip.domain.HoriTipRouteMode;
import koready_backend.horitip.domain.HoriTipScope;
import koready_backend.horitip.domain.HoriTipScopeType;
import koready_backend.horitip.domain.HoriTipStatus;
import koready_backend.horitip.domain.HoriTipStatusTarget;
import koready_backend.horitip.domain.HoriTipTranslation;
import koready_backend.horitip.domain.HoriTipTrigger;
import koready_backend.place.domain.PlaceLanguage;

final class HoriTipDtos {

	private static final String TITLE = "Hori Tip";

	private HoriTipDtos() {
	}

	static AdminHoriTipListResponse from(HoriTipService.HoriTipPage page) {
		return new AdminHoriTipListResponse(
			page.items().stream().map(HoriTipDtos::from).toList(),
			page.nextCursor(),
			page.hasMore());
	}

	static AdminHoriTipResponse from(HoriTipService.HoriTipView tip) {
		HoriTipDraft draft = tip.draft();
		return new AdminHoriTipResponse(
			tip.horiTipId(),
			tip.code(),
			tip.source(),
			tip.status(),
			draft.placement(),
			draft.priority(),
			new HoriTipScopeResponse(
				draft.scope().scopeType(), draft.scope().destinationPlaceIds()),
			HoriTipTriggerResponse.from(draft.trigger()),
			draft.translations().stream()
				.map(translation -> new HoriTipTranslationResponse(
					translation.language(), TITLE, translation.body()))
				.toList(),
			draft.validFrom(),
			draft.validUntil(),
			draft.operatorNote(),
			tip.version(),
			tip.activeNow(),
			tip.editable(),
			tip.createdBySubject(),
			tip.updatedBySubject(),
			tip.activatedAt(),
			tip.archivedAt(),
			tip.createdAt(),
			tip.updatedAt());
	}

	record CreateHoriTipRequest(
		@NotBlank @Size(min = 5, max = 80)
		@Pattern(regexp = "^TIP_[A-Z0-9_]+$") String code,
		@NotNull HoriTipPlacement placement,
		@NotNull @Min(0) @Max(1000) Integer priority,
		@NotNull @Valid HoriTipScopeInput scope,
		@NotNull @Valid HoriTipTriggerInput trigger,
		@NotNull @Size(min = 1, max = 2)
		List<@NotNull @Valid HoriTipTranslationInput> translations,
		Instant validFrom,
		Instant validUntil,
		@Size(max = 500) String operatorNote
	) {
		HoriTipService.CreateCommand toCommand() {
			return new HoriTipService.CreateCommand(code, draft(
				placement,
				priority,
				scope,
				trigger,
				translations,
				validFrom,
				validUntil,
				operatorNote));
		}
	}

	record UpdateHoriTipRequest(
		@NotNull @Min(1) Integer version,
		@NotNull HoriTipPlacement placement,
		@NotNull @Min(0) @Max(1000) Integer priority,
		@NotNull @Valid HoriTipScopeInput scope,
		@NotNull @Valid HoriTipTriggerInput trigger,
		@NotNull @Size(min = 1, max = 2)
		List<@NotNull @Valid HoriTipTranslationInput> translations,
		Instant validFrom,
		Instant validUntil,
		@Size(max = 500) String operatorNote
	) {
		HoriTipService.UpdateCommand toCommand() {
			return new HoriTipService.UpdateCommand(version, draft(
				placement,
				priority,
				scope,
				trigger,
				translations,
				validFrom,
				validUntil,
				operatorNote));
		}
	}

	record UpdateHoriTipStatusRequest(
		@NotNull HoriTipStatusTarget status,
		@NotNull @Min(1) Integer version,
		@NotBlank @Size(max = 500) String reason
	) {
		HoriTipService.StatusCommand toCommand() {
			return new HoriTipService.StatusCommand(status, version, reason);
		}
	}

	record HoriTipScopeInput(
		@NotNull HoriTipScopeType scopeType,
		@NotNull @Size(max = 100) List<@NotNull @Positive Long> destinationPlaceIds
	) {
		HoriTipScope toDomain() {
			return new HoriTipScope(scopeType, destinationPlaceIds);
		}
	}

	record HoriTipTriggerInput(
		@NotNull @Size(max = 8) List<@NotNull HoriTipRouteMode> segmentModes,
		@NotNull @Size(max = 20)
		List<@NotBlank @Size(max = 80) String> routeNameContainsAny,
		@NotNull @Size(max = 20)
		List<@NotBlank @Size(max = 100) String> segmentStartNameContainsAny,
		@NotNull @Size(max = 20)
		List<@NotBlank @Size(max = 100) String> segmentEndNameContainsAny,
		@PositiveOrZero Integer minProviderTotalTimeSeconds,
		@PositiveOrZero Integer minTransferCount,
		@PositiveOrZero Integer minTotalWalkDistanceMeters
	) {
		HoriTipTrigger toDomain() {
			return new HoriTipTrigger(
				segmentModes,
				routeNameContainsAny,
				segmentStartNameContainsAny,
				segmentEndNameContainsAny,
				minProviderTotalTimeSeconds,
				minTransferCount,
				minTotalWalkDistanceMeters);
		}
	}

	record HoriTipTranslationInput(
		@NotNull PlaceLanguage language,
		@NotBlank @Size(max = 300) String body
	) {
		HoriTipTranslation toDomain() {
			return new HoriTipTranslation(language, body);
		}
	}

	record AdminHoriTipListResponse(
		List<AdminHoriTipResponse> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	record AdminHoriTipResponse(
		long horiTipId,
		String code,
		String source,
		HoriTipStatus status,
		HoriTipPlacement placement,
		int priority,
		HoriTipScopeResponse scope,
		HoriTipTriggerResponse trigger,
		List<HoriTipTranslationResponse> translations,
		Instant validFrom,
		Instant validUntil,
		String operatorNote,
		int version,
		boolean activeNow,
		boolean editable,
		String createdBySubject,
		String updatedBySubject,
		Instant activatedAt,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	record HoriTipScopeResponse(
		HoriTipScopeType scopeType,
		List<Long> destinationPlaceIds
	) {
	}

	record HoriTipTriggerResponse(
		List<HoriTipRouteMode> segmentModes,
		List<String> routeNameContainsAny,
		List<String> segmentStartNameContainsAny,
		List<String> segmentEndNameContainsAny,
		Integer minProviderTotalTimeSeconds,
		Integer minTransferCount,
		Integer minTotalWalkDistanceMeters
	) {
		static HoriTipTriggerResponse from(HoriTipTrigger trigger) {
			return new HoriTipTriggerResponse(
				trigger.segmentModes(),
				trigger.routeNameContainsAny(),
				trigger.segmentStartNameContainsAny(),
				trigger.segmentEndNameContainsAny(),
				trigger.minProviderTotalTimeSeconds(),
				trigger.minTransferCount(),
				trigger.minTotalWalkDistanceMeters());
		}
	}

	record HoriTipTranslationResponse(
		PlaceLanguage language,
		String title,
		String body
	) {
	}

	private static HoriTipDraft draft(
		HoriTipPlacement placement,
		int priority,
		HoriTipScopeInput scope,
		HoriTipTriggerInput trigger,
		List<HoriTipTranslationInput> translations,
		Instant validFrom,
		Instant validUntil,
		String operatorNote
	) {
		return new HoriTipDraft(
			placement,
			priority,
			scope.toDomain(),
			trigger.toDomain(),
			translations.stream().map(HoriTipTranslationInput::toDomain).toList(),
			validFrom,
			validUntil,
			operatorNote);
	}
}
