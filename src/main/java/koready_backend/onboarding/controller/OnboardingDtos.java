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
import koready_backend.onboarding.application.OnboardingService;
import koready_backend.onboarding.domain.OnboardingStep;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.domain.NextStep;

final class OnboardingDtos {

	private OnboardingDtos() {
	}

	static ProgressResponse from(OnboardingService.ProgressResult result) {
		return new ProgressResponse(
			result.completed(),
			result.currentStep(),
			result.currentLocationId(),
			result.travelStyles(),
			result.candidateSetId(),
			result.candidateSetVersion(),
			result.selectedPreferencePlaceIds());
	}

	static CompletionResponse from(OnboardingService.CompletionResult result) {
		return new CompletionResponse(
			result.completed(),
			result.completedAt(),
			result.nextStep(),
			new ProfileResponse(
				new LocationResponse(
					result.profile().currentLocation().locationId(),
					result.profile().currentLocation().displayName(),
					result.profile().currentLocation().serviceRegionCode()),
				result.profile().travelStyles(),
				result.profile().selectedPreferencePlaceIds(),
				result.profile().preferenceTags().stream()
					.map(tag -> new PreferenceTagResponse(
						tag.tagId(), tag.code(), tag.name(), tag.weight(), tag.source()))
					.toList()));
	}

	record CompletionRequest(
		@NotNull @Positive Long currentLocationId,
		@NotNull @Size(min = 1, max = 4)
		List<@NotNull TravelStyle> travelStyles,
		@NotBlank @Size(max = 100) String candidateSetId,
		@NotNull @Min(1) @Max(Integer.MAX_VALUE) Integer candidateSetVersion,
		@NotNull @Size(min = 1, max = 3)
		List<@NotNull @Positive Long> selectedPreferencePlaceIds
	) {
		OnboardingService.CompletionCommand toCommand() {
			return new OnboardingService.CompletionCommand(
				currentLocationId,
				travelStyles,
				candidateSetId,
				candidateSetVersion,
				selectedPreferencePlaceIds);
		}
	}

	record ProgressResponse(
		boolean completed,
		OnboardingStep currentStep,
		Long currentLocationId,
		List<TravelStyle> travelStyles,
		String candidateSetId,
		Integer candidateSetVersion,
		List<Long> selectedPreferencePlaceIds
	) {
	}

	record CompletionResponse(
		boolean completed,
		Instant completedAt,
		NextStep nextStep,
		@Valid ProfileResponse profile
	) {
	}

	record ProfileResponse(
		LocationResponse currentLocation,
		List<TravelStyle> travelStyles,
		List<Long> selectedPreferencePlaceIds,
		List<PreferenceTagResponse> preferenceTags
	) {
	}

	record LocationResponse(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}

	record PreferenceTagResponse(
		long tagId,
		String code,
		String name,
		double weight,
		String source
	) {
	}
}
