package koready_backend.recommendation.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.domain.RecommendationScope;

public interface RecommendationDeckRepository {

	Optional<UserRecommendationContext> findUserContext(
		String userPublicId,
		Long requestedLocationId
	);

	List<RecommendationCandidate> findEligibleCandidates(
		long userId,
		Instant now,
		PlaceLanguage language,
		RecommendationScope scope,
		ServiceRegionCode originServiceRegionCode,
		int limit
	);

	StoredDeckPage createDeck(CreateDeckPlan plan);

	Optional<StoredDeckPage> findPage(
		String userPublicId,
		String deckPublicId,
		String cursor,
		Instant now
	);

	record UserRecommendationContext(
		long userId,
		String userPublicId,
		long locationId,
		String locationDisplayName,
		ServiceRegionCode serviceRegionCode,
		List<TravelStyle> travelStyles
	) {
	}

	record RecommendationCandidate(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String locationText,
		String imageUrl,
		String overview,
		List<TravelStyle> travelStyles,
		BigDecimal qualityScore
	) {
	}

	record CardSnapshot(
		long placeId,
		String title,
		String locationText,
		String imageUrl,
		String shortDescription,
		ServiceRegionCode serviceRegionCode,
		TravelStyle travelStyle,
		List<String> tags,
		int matchRank,
		boolean travelStyleMatched,
		boolean preferenceTagMatched,
		List<String> matchedTagCodes
	) {
	}

	record PagePlan(
		int pageNumber,
		String cursor,
		int startOrder,
		int endOrder
	) {
	}

	record CreateDeckPlan(
		String deckPublicId,
		long userId,
		String userPublicId,
		RecommendationScope scope,
		long originLocationId,
		String originDisplayName,
		ServiceRegionCode originServiceRegionCode,
		PlaceLanguage language,
		String seed,
		int cursorVersion,
		String suppressionPolicyVersion,
		int suppressionDays,
		int pageSize,
		Instant createdAt,
		Instant expiresAt,
		List<CardSnapshot> items,
		List<PagePlan> pages
	) {
	}

	record StoredDeckPage(
		String deckPublicId,
		RecommendationScope scope,
		long originLocationId,
		String originDisplayName,
		ServiceRegionCode originServiceRegionCode,
		List<CardSnapshot> cards,
		String nextCursor,
		boolean hasMore,
		String suppressionPolicyVersion,
		int suppressionDays
	) {
	}
}
