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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

	private RecommendationDeckService service;

	@BeforeEach
	void setUp() {
		service = new RecommendationDeckService(
			repository,
			Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
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
		assertEquals(List.of(1L, 2L, 3L, 4L),
			plan.items().stream().map(CardSnapshot::placeId).toList());
		assertEquals(List.of(2, 3, 2, 3),
			plan.items().stream().map(CardSnapshot::matchRank).toList());
		assertEquals(2, plan.pages().size());
		assertEquals(2, result.cards().size());
		assertTrue(result.hasMore());
		assertEquals(30, result.deduplication().suppressionDays());
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
		return new RecommendationCandidate(
			placeId,
			"Place " + placeId,
			region,
			region.name(),
			null,
			"Description " + placeId,
			List.of(style),
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
