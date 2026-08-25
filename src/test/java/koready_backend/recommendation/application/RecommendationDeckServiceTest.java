package koready_backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.place.application.port.SavedPlaceStatusPort;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.exception.RecommendationContextUnavailableException;
import koready_backend.recommendation.application.port.RecommendationDeckRepository;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CardSnapshot;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CreateDeckPlan;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.RecommendationCandidate;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.StoredDeckPage;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.UserRecommendationContext;
import koready_backend.recommendation.domain.RecommendationScope;

@ExtendWith(MockitoExtension.class)
class RecommendationDeckServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");
	private static final String USER_PUBLIC_ID = "usr_01K0KOREADYTEST";

	@Mock
	RecommendationDeckRepository repository;

	@Mock
	SavedPlaceStatusPort savedPlaceStatusPort;

	private RecommendationDeckService service;

	@BeforeEach
	void setUp() {
		service = new RecommendationDeckService(
			repository,
			savedPlaceStatusPort,
			Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
	}

	@Test
	void ordersDeckByEndedStatusThenHeartsAndInternalScore() {
		when(repository.findUserContext(USER_PUBLIC_ID, null))
			.thenReturn(Optional.of(context()));
		when(repository.findEligibleCandidates(
			eq(7L), eq(NOW), eq(PlaceLanguage.KO),
			eq(RecommendationScope.NATIONWIDE), eq(ServiceRegionCode.SEOUL),
			any(Integer.class)))
			.thenReturn(List.of(
				candidate(1L, ServiceRegionCode.SEOUL, TravelStyle.LOCAL_FOOD,
					"50.00", 2L, false),
				candidate(2L, ServiceRegionCode.SEOUL, TravelStyle.NATURE,
					"100.00", 0L, false),
				candidate(3L, ServiceRegionCode.SEOUL, TravelStyle.NATURE,
					"100.00", 100L, true)));
		when(repository.createDeck(any())).thenAnswer(invocation ->
			storedFirstPage(invocation.getArgument(0)));

		service.createDeck(
			USER_PUBLIC_ID, RecommendationScope.NATIONWIDE, null, 20,
			PlaceLanguage.KO);

		ArgumentCaptor<CreateDeckPlan> captor =
			ArgumentCaptor.forClass(CreateDeckPlan.class);
		org.mockito.Mockito.verify(repository).createDeck(captor.capture());
		assertEquals(List.of(1L, 2L, 3L), captor.getValue().items().stream()
			.map(CardSnapshot::placeId).toList());
	}

	@Test
	void prefersUnseenThenAdminPriorityButKeepsSuppressedFallbackCards() {
		when(repository.findUserContext(USER_PUBLIC_ID, null))
			.thenReturn(Optional.of(context()));
		when(repository.findEligibleCandidates(
			eq(7L), eq(NOW), eq(PlaceLanguage.KO),
			eq(RecommendationScope.NATIONWIDE), eq(ServiceRegionCode.SEOUL),
			any(Integer.class)))
			.thenReturn(List.of(
				candidate(1L, ServiceRegionCode.SEOUL, TravelStyle.NATURE,
					"90.00", 0L, false, 1000, true),
				candidate(2L, ServiceRegionCode.SEOUL, TravelStyle.NATURE,
					"80.00", 0L, false, 100, false),
				candidate(3L, ServiceRegionCode.SEOUL, TravelStyle.NATURE,
					"70.00", 0L, false, 900, false)));
		when(repository.createDeck(any())).thenAnswer(invocation ->
			storedFirstPage(invocation.getArgument(0)));

		service.createDeck(
			USER_PUBLIC_ID, RecommendationScope.NATIONWIDE, null, 20,
			PlaceLanguage.KO);

		ArgumentCaptor<CreateDeckPlan> captor =
			ArgumentCaptor.forClass(CreateDeckPlan.class);
		org.mockito.Mockito.verify(repository).createDeck(captor.capture());
		assertEquals(List.of(3L, 2L, 1L), captor.getValue().items().stream()
			.map(CardSnapshot::placeId).toList());
	}

	@Test
	void reflectsTheAuthenticatedUsersSavedStateOnTheReturnedDeckPage() {
		when(repository.findUserContext(USER_PUBLIC_ID, null))
			.thenReturn(Optional.of(context()));
		when(repository.findEligibleCandidates(
			eq(7L),
			eq(NOW),
			eq(PlaceLanguage.KO),
			eq(RecommendationScope.NEARBY),
			eq(ServiceRegionCode.SEOUL),
			any(Integer.class)))
			.thenReturn(List.of(
				candidate(1L, ServiceRegionCode.SEOUL, TravelStyle.NATURE, "90.00"),
				candidate(2L, ServiceRegionCode.SEOUL, TravelStyle.NATURE, "80.00")));
		when(repository.createDeck(any())).thenAnswer(invocation ->
			storedFirstPage(invocation.getArgument(0)));
		when(savedPlaceStatusPort.findSavedPlaceIds(
			USER_PUBLIC_ID, List.of(1L, 2L)))
			.thenReturn(Set.of(2L));

		var result = service.createDeck(
			USER_PUBLIC_ID,
			RecommendationScope.NEARBY,
			null,
			20,
			PlaceLanguage.KO);

		assertEquals(List.of(false, true),
			result.cards().stream()
				.map(RecommendationDeckService.RecommendationCard::saved)
				.toList());
	}

	@Test
	void buildsNearbyDeckByRegionThenStyleMatchAndRemovesDuplicates() {
		when(repository.findUserContext(USER_PUBLIC_ID, null))
			.thenReturn(Optional.of(context()));
		when(repository.findEligibleCandidates(
			eq(7L),
			eq(NOW),
			eq(PlaceLanguage.EN),
			eq(RecommendationScope.NEARBY),
			eq(ServiceRegionCode.SEOUL),
			any(Integer.class)))
			.thenReturn(List.of(
				candidate(1L, ServiceRegionCode.SEOUL, TravelStyle.NATURE, "70.00"),
				candidate(2L, ServiceRegionCode.SEOUL, TravelStyle.LOCAL_FOOD, "99.00"),
				candidate(3L, ServiceRegionCode.GANGWON, TravelStyle.NATURE, "100.00"),
				candidate(4L, ServiceRegionCode.GANGWON, TravelStyle.LOCAL_FOOD, "100.00"),
				candidate(1L, ServiceRegionCode.SEOUL, TravelStyle.NATURE, "70.00")));
		when(repository.createDeck(any())).thenAnswer(invocation ->
			storedFirstPage(invocation.getArgument(0)));

		RecommendationDeckService.RecommendationDeckPage result = service.createDeck(
			USER_PUBLIC_ID,
			RecommendationScope.NEARBY,
			null,
			2,
			PlaceLanguage.EN);

		ArgumentCaptor<CreateDeckPlan> planCaptor =
			ArgumentCaptor.forClass(CreateDeckPlan.class);
		org.mockito.Mockito.verify(repository).createDeck(planCaptor.capture());
		CreateDeckPlan plan = planCaptor.getValue();
		assertEquals(List.of(1L, 2L),
			plan.items().stream().map(CardSnapshot::placeId).toList());
		assertEquals(List.of(2, 3),
			plan.items().stream().map(CardSnapshot::matchRank).toList());
		assertEquals(1, plan.pages().size());
		assertEquals(2, result.cards().size());
		assertFalse(result.hasMore());
		assertEquals(14, result.deduplication().suppressionDays());
		assertTrue(result.deduplication().guaranteedWithinDeck());
	}

	@Test
	void createsAnEmptyStableDeckWhenNoCandidateRemains() {
		when(repository.findUserContext(USER_PUBLIC_ID, null))
			.thenReturn(Optional.of(context()));
		when(repository.findEligibleCandidates(
			eq(7L),
			eq(NOW),
			eq(PlaceLanguage.KO),
			eq(RecommendationScope.NATIONWIDE),
			eq(ServiceRegionCode.SEOUL),
			any(Integer.class)))
			.thenReturn(List.of());
		when(repository.createDeck(any())).thenAnswer(invocation ->
			storedFirstPage(invocation.getArgument(0)));

		RecommendationDeckService.RecommendationDeckPage result = service.createDeck(
			USER_PUBLIC_ID,
			RecommendationScope.NATIONWIDE,
			null,
			20,
			PlaceLanguage.KO);

		assertTrue(result.cards().isEmpty());
		assertFalse(result.hasMore());
		assertEquals(0, result.cards().size());
	}

	@Test
	void rejectsAUserWithoutAnOwnedActiveOriginLocation() {
		when(repository.findUserContext(USER_PUBLIC_ID, 999L)).thenReturn(Optional.empty());

		assertThrows(
			RecommendationContextUnavailableException.class,
			() -> service.createDeck(
				USER_PUBLIC_ID,
				RecommendationScope.NEARBY,
				999L,
				20,
				PlaceLanguage.KO));
	}

	private UserRecommendationContext context() {
		return new UserRecommendationContext(
			7L,
			USER_PUBLIC_ID,
			10L,
			"Campus",
			ServiceRegionCode.SEOUL,
			List.of(TravelStyle.NATURE));
	}

	private RecommendationCandidate candidate(
		long placeId,
		ServiceRegionCode region,
		TravelStyle style,
		String qualityScore
	) {
		return candidate(placeId, region, style, qualityScore, 0L, false);
	}

	private RecommendationCandidate candidate(
		long placeId,
		ServiceRegionCode region,
		TravelStyle style,
		String qualityScore,
		long heartCount,
		boolean endedFestival
	) {
		return candidate(
			placeId, region, style, qualityScore, heartCount, endedFestival, 0, false);
	}

	private RecommendationCandidate candidate(
		long placeId,
		ServiceRegionCode region,
		TravelStyle style,
		String qualityScore,
		long heartCount,
		boolean endedFestival,
		int curationPriority,
		boolean suppressed
	) {
		return new RecommendationCandidate(
			placeId,
			"Place " + placeId,
			region,
			region.name(),
			null,
			"Description " + placeId,
			List.of(style),
			heartCount,
			endedFestival,
			curationPriority,
			suppressed,
			new BigDecimal(qualityScore));
	}

	private StoredDeckPage storedFirstPage(CreateDeckPlan plan) {
		int end = Math.min(plan.pageSize(), plan.items().size());
		List<CardSnapshot> firstCards = plan.items().subList(0, end);
		String nextCursor = plan.pages().size() > 1
			? plan.pages().get(1).cursor()
			: null;
		return new StoredDeckPage(
			plan.deckPublicId(),
			plan.scope(),
			plan.originLocationId(),
			plan.originDisplayName(),
			plan.originServiceRegionCode(),
			firstCards,
			nextCursor,
			nextCursor != null,
			plan.suppressionPolicyVersion(),
			plan.suppressionDays());
	}
}
