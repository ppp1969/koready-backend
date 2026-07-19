package koready_backend.onboarding.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.onboarding.application.OnboardingService.CompletionCommand;
import koready_backend.onboarding.application.exception.OnboardingCompletionException;
import koready_backend.onboarding.application.exception.OnboardingCompletionException.Reason;
import koready_backend.onboarding.application.port.OnboardingProfileRepository;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.CandidateSetRecord;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.LocationRecord;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.SelectionRecord;
import koready_backend.onboarding.application.port.OnboardingProfileRepository.UserRecord;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.onboarding.domain.OnboardingStep;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.user.application.exception.UserUnavailableException;
import koready_backend.user.domain.NextStep;
import koready_backend.user.domain.SignupStatus;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T05:00:00Z");
	private static final Instant COMPLETED_AT = Instant.parse("2026-07-18T05:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Mock
	private OnboardingProfileRepository repository;

	private OnboardingService service;

	@BeforeEach
	void setUp() {
		service = new OnboardingService(repository, CLOCK);
	}

	@Test
	void restoresTheFirstIncompleteStepFromPersistedState() {
		when(repository.findUserByPublicId("usr_onboarding"))
			.thenReturn(Optional.of(user(SignupStatus.NEED_ONBOARDING, null, null)));
		when(repository.findTravelStyles(7L)).thenReturn(List.of());
		when(repository.findSelection(7L)).thenReturn(Optional.empty());

		var progress = service.getProgress("usr_onboarding");

		assertEquals(OnboardingStep.LOCATION, progress.currentStep());
		assertEquals(List.of(), progress.travelStyles());
		assertEquals(List.of(), progress.selectedPreferencePlaceIds());
	}

	@Test
	void restoresPreferencePlaceStepWhenLocationAndStylesExist() {
		when(repository.findUserByPublicId("usr_onboarding"))
			.thenReturn(Optional.of(user(SignupStatus.NEED_ONBOARDING, 11L, null)));
		when(repository.findOwnedLocation(7L, 11L)).thenReturn(Optional.of(location(true)));
		when(repository.findTravelStyles(7L))
			.thenReturn(List.of(TravelStyle.LOCAL_FOOD));
		when(repository.findSelection(7L)).thenReturn(Optional.empty());

		var progress = service.getProgress("usr_onboarding");

		assertEquals(OnboardingStep.PREFERENCE_PLACES, progress.currentStep());
		assertEquals(11L, progress.currentLocationId());
	}

	@Test
	void completesAllSelectionsAtomically() {
		CompletionCommand command = command();
		when(repository.findUserByPublicIdForUpdate("usr_onboarding"))
			.thenReturn(Optional.of(user(SignupStatus.NEED_ONBOARDING, null, null)));
		when(repository.findOwnedLocation(7L, 11L)).thenReturn(Optional.of(location(true)));
		when(repository.findCandidateSet("onb-v1"))
			.thenReturn(Optional.of(candidate(CandidateSetStatus.ARCHIVED, COMPLETED_AT)));
		when(repository.findCandidatePlaceIds(31L)).thenReturn(Set.of(101L, 102L, 103L));

		var result = service.complete("usr_onboarding", command);

		assertTrue(result.completed());
		assertEquals(NOW, result.completedAt());
		assertEquals(NextStep.COMPLETED, result.nextStep());
		assertEquals(List.of(), result.profile().preferenceTags());
		verify(repository).replaceTravelStyles(7L, command.travelStyles(), NOW);
		verify(repository).replaceSelections(7L, 31L, command.selectedPreferencePlaceIds(), NOW);
		verify(repository).completeUser(7L, 11L, NOW);
	}

	@Test
	void returnsTheOriginalCompletionForAnIdenticalRetry() {
		CompletionCommand command = command();
		when(repository.findUserByPublicIdForUpdate("usr_onboarding"))
			.thenReturn(Optional.of(user(SignupStatus.COMPLETED, 11L, COMPLETED_AT)));
		when(repository.findOwnedLocation(7L, 11L)).thenReturn(Optional.of(location(false)));
		when(repository.findTravelStyles(7L)).thenReturn(command.travelStyles());
		when(repository.findSelection(7L)).thenReturn(Optional.of(selection()));

		var result = service.complete("usr_onboarding", command);

		assertEquals(COMPLETED_AT, result.completedAt());
		verify(repository, never()).replaceTravelStyles(7L, command.travelStyles(), NOW);
		verify(repository, never()).completeUser(7L, 11L, NOW);
	}

	@Test
	void rejectsACompletedProfileOverwrite() {
		when(repository.findUserByPublicIdForUpdate("usr_onboarding"))
			.thenReturn(Optional.of(user(SignupStatus.COMPLETED, 11L, COMPLETED_AT)));
		when(repository.findOwnedLocation(7L, 11L)).thenReturn(Optional.of(location(true)));
		when(repository.findTravelStyles(7L)).thenReturn(List.of(TravelStyle.NATURE));
		when(repository.findSelection(7L)).thenReturn(Optional.of(selection()));

		OnboardingCompletionException exception = assertThrows(
			OnboardingCompletionException.class,
			() -> service.complete("usr_onboarding", command()));

		assertEquals(Reason.ALREADY_COMPLETED, exception.reason());
		verify(repository, never()).completeUser(7L, 11L, NOW);
	}

	@Test
	void rejectsInvalidOwnershipPublicationVersionAndSelections() {
		when(repository.findUserByPublicIdForUpdate("usr_onboarding"))
			.thenReturn(Optional.of(user(SignupStatus.NEED_ONBOARDING, null, null)));
		when(repository.findOwnedLocation(7L, 11L)).thenReturn(Optional.empty());
		assertReason(Reason.LOCATION_INVALID, command());

		when(repository.findOwnedLocation(7L, 11L)).thenReturn(Optional.of(location(true)));
		when(repository.findCandidateSet("onb-v1"))
			.thenReturn(Optional.of(candidate(CandidateSetStatus.DRAFT, null)));
		assertReason(Reason.CANDIDATE_SET_INVALID, command());

		when(repository.findCandidateSet("onb-v1"))
			.thenReturn(Optional.of(new CandidateSetRecord(
				31L, "onb-v1", 2, CandidateSetStatus.PUBLISHED, COMPLETED_AT)));
		assertReason(Reason.CANDIDATE_SET_VERSION_MISMATCH, command());

		when(repository.findCandidateSet("onb-v1"))
			.thenReturn(Optional.of(candidate(CandidateSetStatus.PUBLISHED, COMPLETED_AT)));
		when(repository.findCandidatePlaceIds(31L)).thenReturn(Set.of(101L));
		assertReason(Reason.SELECTION_INVALID, command());
	}

	@Test
	void rejectsDuplicateValuesAndAnUnavailablePrincipal() {
		CompletionCommand duplicateStyles = new CompletionCommand(
			11L,
			List.of(TravelStyle.NATURE, TravelStyle.NATURE),
			"onb-v1",
			1,
			List.of(101L));
		assertReason(Reason.TRAVEL_STYLES_INVALID, duplicateStyles);

		when(repository.findUserByPublicIdForUpdate("usr_missing"))
			.thenReturn(Optional.empty());
		assertThrows(UserUnavailableException.class,
			() -> service.complete("usr_missing", command()));
	}

	private void assertReason(Reason reason, CompletionCommand command) {
		OnboardingCompletionException exception = assertThrows(
			OnboardingCompletionException.class,
			() -> service.complete("usr_onboarding", command));
		assertEquals(reason, exception.reason());
	}

	private static CompletionCommand command() {
		return new CompletionCommand(
			11L,
			List.of(TravelStyle.LOCAL_FOOD, TravelStyle.LOCAL_FESTIVAL),
			"onb-v1",
			1,
			List.of(101L, 102L));
	}

	private static UserRecord user(
		SignupStatus status,
		Long locationId,
		Instant completedAt
	) {
		return new UserRecord(7L, status, locationId, completedAt);
	}

	private static LocationRecord location(boolean active) {
		return new LocationRecord(
			11L, "성신여자대학교", ServiceRegionCode.SEOUL, active);
	}

	private static CandidateSetRecord candidate(
		CandidateSetStatus status,
		Instant publishedAt
	) {
		return new CandidateSetRecord(31L, "onb-v1", 1, status, publishedAt);
	}

	private static SelectionRecord selection() {
		return new SelectionRecord(31L, "onb-v1", 1, List.of(101L, 102L));
	}
}
