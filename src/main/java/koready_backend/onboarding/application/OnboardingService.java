package koready_backend.onboarding.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.onboarding.application.exception.OnboardingCompletionException;
import koready_backend.onboarding.application.exception.OnboardingCompletionException.Reason;
import koready_backend.onboarding.application.port.OnboardingProfileRepository;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.CandidateSetRecord;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.LocationRecord;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.SelectionRecord;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.UserRecord;
import koready_backend.onboarding.domain.OnboardingStep;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

@Service
public class OnboardingService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final OnboardingProfileRepository repository;
	private final Clock clock;

	@Autowired
	public OnboardingService(OnboardingProfileRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	OnboardingService(OnboardingProfileRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ProgressResult getProgress(String userPublicId) {
		UserRecord user = repository.findUserByPublicId(userPublicId)
			.orElseThrow(UserUnavailableException::new);
		List<TravelStyle> styles = repository.findTravelStyles(user.userId());
		SelectionRecord selection = repository.findSelection(user.userId()).orElse(null);

		Long locationId = activeProgressLocationId(user);
		boolean completed = user.signupStatus() == SignupStatus.COMPLETED;
		OnboardingStep step = currentStep(completed, locationId, styles);
		return new ProgressResult(
			completed,
			step,
			locationId,
			styles,
			selection == null ? null : selection.candidateSetPublicId(),
			selection == null ? null : selection.candidateSetVersion(),
			selection == null ? List.of() : selection.placeIds());
	}

	@Transactional
	public CompletionResult complete(String userPublicId, CompletionCommand command) {
		validateCommand(command);
		UserRecord user = repository.findUserByPublicIdForUpdate(userPublicId)
			.orElseThrow(UserUnavailableException::new);

		if (user.signupStatus() == SignupStatus.COMPLETED) {
			return completedRetry(user, command);
		}
		if (user.signupStatus() != SignupStatus.NEED_ONBOARDING) {
			throw error(
				Reason.PREREQUISITE_INCOMPLETE,
				"Terms and language selection must be completed first.");
		}

		LocationRecord location = repository
			.findOwnedLocation(user.userId(), command.currentLocationId())
			.filter(LocationRecord::active)
			.orElseThrow(() -> error(
				Reason.LOCATION_INVALID,
				"The location must be active and owned by the user."));
		CandidateSetRecord candidateSet = repository.findCandidateSet(command.candidateSetId())
			.orElseThrow(() -> error(
				Reason.CANDIDATE_SET_INVALID,
				"The onboarding candidate set does not exist."));
		validateCandidateSet(candidateSet, command);

		if (!repository.findCandidatePlaceIds(candidateSet.candidateSetId())
			.containsAll(command.selectedPreferencePlaceIds())) {
			throw error(
				Reason.SELECTION_INVALID,
				"Every selected place must belong to the submitted candidate set.");
		}

		Instant completedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
		repository.replaceTravelStyles(user.userId(), command.travelStyles(), completedAt);
		repository.replaceSelections(
			user.userId(),
			candidateSet.candidateSetId(),
			command.selectedPreferencePlaceIds(),
			completedAt);
		repository.completeUser(user.userId(), command.currentLocationId(), completedAt);
		return completion(completedAt, location, command.travelStyles(),
			command.selectedPreferencePlaceIds());
	}

	private CompletionResult completedRetry(UserRecord user, CompletionCommand command) {
		LocationRecord location = user.defaultLocationId() == null
			? null
			: repository.findOwnedLocation(user.userId(), user.defaultLocationId()).orElse(null);
		List<TravelStyle> styles = repository.findTravelStyles(user.userId());
		SelectionRecord selection = repository.findSelection(user.userId()).orElse(null);

		boolean same = location != null
			&& user.completedAt() != null
			&& Objects.equals(user.defaultLocationId(), command.currentLocationId())
			&& styles.equals(command.travelStyles())
			&& selection != null
			&& selection.candidateSetPublicId().equals(command.candidateSetId())
			&& selection.candidateSetVersion() == command.candidateSetVersion()
			&& selection.placeIds().equals(command.selectedPreferencePlaceIds());
		if (!same) {
			throw error(
				Reason.ALREADY_COMPLETED,
				"Onboarding is already completed with different selections.");
		}
		return completion(
			user.completedAt(), location, styles, selection.placeIds());
	}

	private Long activeProgressLocationId(UserRecord user) {
		if (user.defaultLocationId() == null) {
			return null;
		}
		return repository.findOwnedLocation(user.userId(), user.defaultLocationId())
			.filter(location -> user.signupStatus() == SignupStatus.COMPLETED
				|| location.active())
			.map(LocationRecord::locationId)
			.orElse(null);
	}

	private static OnboardingStep currentStep(
		boolean completed,
		Long locationId,
		List<TravelStyle> styles
	) {
		if (completed) {
			return OnboardingStep.COMPLETED;
		}
		if (locationId == null) {
			return OnboardingStep.LOCATION;
		}
		if (styles.isEmpty()) {
			return OnboardingStep.TRAVEL_STYLES;
		}
		return OnboardingStep.PREFERENCE_PLACES;
	}

	private static void validateCommand(CompletionCommand command) {
		if (command == null) {
			throw error(Reason.SELECTION_INVALID, "The onboarding request is required.");
		}
		if (command.currentLocationId() <= 0) {
			throw error(Reason.LOCATION_INVALID, "The location ID must be positive.");
		}
		if (!validUniqueList(command.travelStyles(), 1, 4)) {
			throw error(
				Reason.TRAVEL_STYLES_INVALID,
				"Travel styles must contain one to four unique values.");
		}
		if (command.candidateSetId() == null || command.candidateSetId().isBlank()
			|| command.candidateSetVersion() < 1) {
			throw error(
				Reason.CANDIDATE_SET_INVALID,
				"A candidate set ID and positive version are required.");
		}
		if (!validUniqueList(command.selectedPreferencePlaceIds(), 1, 3)
			|| command.selectedPreferencePlaceIds().stream().anyMatch(id -> id <= 0)) {
			throw error(
				Reason.SELECTION_INVALID,
				"Selected places must contain one to three unique positive IDs.");
		}
	}

	private static boolean validUniqueList(List<?> values, int min, int max) {
		return values != null
			&& values.size() >= min
			&& values.size() <= max
			&& values.stream().noneMatch(Objects::isNull)
			&& new HashSet<>(values).size() == values.size();
	}

	private static void validateCandidateSet(
		CandidateSetRecord candidateSet,
		CompletionCommand command
	) {
		if (candidateSet.version() == null
			|| candidateSet.version() != command.candidateSetVersion()) {
			throw error(
				Reason.CANDIDATE_SET_VERSION_MISMATCH,
				"The candidate set version does not match.");
		}
		if (candidateSet.publishedAt() == null) {
			throw error(
				Reason.CANDIDATE_SET_INVALID,
				"Only a candidate set with a publication history can be submitted.");
		}
	}

	private static CompletionResult completion(
		Instant completedAt,
		LocationRecord location,
		List<TravelStyle> styles,
		List<Long> placeIds
	) {
		return new CompletionResult(
			true,
			completedAt,
			NextStep.COMPLETED,
			new ProfileResult(
				new LocationResult(
					location.locationId(),
					location.displayName(),
					location.serviceRegionCode()),
				List.copyOf(styles),
				List.copyOf(placeIds),
				List.of()));
	}

	private static OnboardingCompletionException error(Reason reason, String message) {
		return new OnboardingCompletionException(reason, message);
	}

	public record CompletionCommand(
		long currentLocationId,
		List<TravelStyle> travelStyles,
		String candidateSetId,
		int candidateSetVersion,
		List<Long> selectedPreferencePlaceIds
	) {
		public CompletionCommand {
			travelStyles = immutableCopyAllowingNulls(travelStyles);
			selectedPreferencePlaceIds = selectedPreferencePlaceIds == null
				? null
				: immutableCopyAllowingNulls(selectedPreferencePlaceIds);
		}

		private static <T> List<T> immutableCopyAllowingNulls(List<T> values) {
			return values == null
				? null
				: Collections.unmodifiableList(new ArrayList<>(values));
		}
	}

	public record ProgressResult(
		boolean completed,
		OnboardingStep currentStep,
		Long currentLocationId,
		List<TravelStyle> travelStyles,
		String candidateSetId,
		Integer candidateSetVersion,
		List<Long> selectedPreferencePlaceIds
	) {
	}

	public record CompletionResult(
		boolean completed,
		Instant completedAt,
		NextStep nextStep,
		ProfileResult profile
	) {
	}

	public record ProfileResult(
		LocationResult currentLocation,
		List<TravelStyle> travelStyles,
		List<Long> selectedPreferencePlaceIds,
		List<PreferenceTagResult> preferenceTags
	) {
	}

	public record LocationResult(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}

	public record PreferenceTagResult(
		long tagId,
		String code,
		String name,
		double weight,
		String source
	) {
	}
}
