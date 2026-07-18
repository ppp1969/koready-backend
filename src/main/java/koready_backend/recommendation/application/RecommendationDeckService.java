package koready_backend.recommendation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.exception.RecommendationContextUnavailableException;
import koready_backend.recommendation.application.exception.RecommendationDeckNotFoundException;
import koready_backend.recommendation.application.port.RecommendationDeckRepository;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CardSnapshot;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CreateDeckPlan;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.PagePlan;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.RecommendationCandidate;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.StoredDeckPage;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.UserRecommendationContext;
import koready_backend.recommendation.domain.RecommendationScope;

@Service
public class RecommendationDeckService {

	public static final String SUPPRESSION_POLICY_VERSION = "recommendation-suppression-v1";
	public static final int SUPPRESSION_DAYS = 30;
	private static final int CURSOR_VERSION = 1;
	private static final int MAX_CANDIDATES = 500;
	private static final int MAX_DECK_ITEMS = 200;
	private static final int REMAINING_THRESHOLD = 5;
	private static final int SHORT_DESCRIPTION_LENGTH = 160;
	private static final Duration DECK_TTL = Duration.ofHours(24);

	private final RecommendationDeckRepository repository;
	private final Clock clock;

	@Autowired
	public RecommendationDeckService(RecommendationDeckRepository repository) {
		this(repository, Clock.systemUTC());
	}

	RecommendationDeckService(RecommendationDeckRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public RecommendationDeckPage createDeck(
		String userPublicId,
		RecommendationScope scope,
		Long originLocationId,
		int pageSize,
		PlaceLanguage language
	) {
		Instant now = clock.instant();
		UserRecommendationContext context = repository
			.findUserContext(userPublicId, originLocationId)
			.orElseThrow(RecommendationContextUnavailableException::new);
		List<RecommendationCandidate> candidates = repository.findEligibleCandidates(
			context.userId(),
			now,
			language,
			scope,
			context.serviceRegionCode(),
			MAX_CANDIDATES);
		String deckPublicId = "rec_" + UUID.randomUUID();
		String seed = sha256(deckPublicId);
		List<CardSnapshot> items = selectCards(candidates, context, scope, seed);
		List<PagePlan> pages = pages(items.size(), pageSize);
		CreateDeckPlan plan = new CreateDeckPlan(
			deckPublicId,
			context.userId(),
			context.userPublicId(),
			scope,
			context.locationId(),
			context.locationDisplayName(),
			context.serviceRegionCode(),
			language,
			seed,
			CURSOR_VERSION,
			SUPPRESSION_POLICY_VERSION,
			SUPPRESSION_DAYS,
			pageSize,
			now,
			now.plus(DECK_TTL),
			items,
			pages);
		return page(repository.createDeck(plan));
	}

	public RecommendationDeckPage getPage(
		String userPublicId,
		String deckPublicId,
		String cursor
	) {
		StoredDeckPage stored = repository.findPage(
			userPublicId, deckPublicId, cursor, clock.instant())
			.orElseThrow(RecommendationDeckNotFoundException::new);
		return page(stored);
	}

	private List<CardSnapshot> selectCards(
		List<RecommendationCandidate> candidates,
		UserRecommendationContext context,
		RecommendationScope scope,
		String seed
	) {
		LinkedHashMap<Long, RecommendationCandidate> unique = new LinkedHashMap<>();
		for (RecommendationCandidate candidate : candidates) {
			unique.putIfAbsent(candidate.placeId(), candidate);
		}
		return unique.values().stream()
			.sorted(Comparator
				.comparingInt((RecommendationCandidate candidate) ->
					regionRank(candidate, context.serviceRegionCode(), scope))
				.thenComparingInt(candidate -> matchRank(candidate, context.travelStyles()))
				.thenComparing(RecommendationCandidate::qualityScore, Comparator.reverseOrder())
				.thenComparing(candidate -> tieBreak(seed, candidate.placeId())))
			.limit(MAX_DECK_ITEMS)
			.map(candidate -> snapshot(candidate, context.travelStyles()))
			.toList();
	}

	private static int regionRank(
		RecommendationCandidate candidate,
		ServiceRegionCode originRegion,
		RecommendationScope scope
	) {
		if (scope == RecommendationScope.NATIONWIDE) {
			return 0;
		}
		return candidate.serviceRegionCode() == originRegion ? 0 : 1;
	}

	private static int matchRank(
		RecommendationCandidate candidate,
		List<TravelStyle> userStyles
	) {
		return matchedStyle(candidate, userStyles) == null ? 3 : 2;
	}

	private static CardSnapshot snapshot(
		RecommendationCandidate candidate,
		List<TravelStyle> userStyles
	) {
		TravelStyle matched = matchedStyle(candidate, userStyles);
		TravelStyle primary = matched != null
			? matched
			: candidate.travelStyles().stream().findFirst().orElse(null);
		return new CardSnapshot(
			candidate.placeId(),
			candidate.title(),
			candidate.locationText(),
			candidate.imageUrl(),
			shortDescription(candidate.overview()),
			candidate.serviceRegionCode(),
			primary,
			candidate.travelStyles().stream().map(Enum::name).toList(),
			matched == null ? 3 : 2,
			matched != null,
			false,
			List.of());
	}

	private static TravelStyle matchedStyle(
		RecommendationCandidate candidate,
		List<TravelStyle> userStyles
	) {
		for (TravelStyle userStyle : userStyles) {
			if (candidate.travelStyles().contains(userStyle)) {
				return userStyle;
			}
		}
		return null;
	}

	private static List<PagePlan> pages(int itemCount, int pageSize) {
		int pageCount = Math.max(1, (itemCount + pageSize - 1) / pageSize);
		List<PagePlan> pages = new ArrayList<>(pageCount);
		for (int index = 0; index < pageCount; index++) {
			int startOrder = index * pageSize + 1;
			int endOrder = Math.min(itemCount, (index + 1) * pageSize);
			pages.add(new PagePlan(
				index + 1,
				"reccur_" + UUID.randomUUID(),
				startOrder,
				endOrder));
		}
		return List.copyOf(pages);
	}

	private static RecommendationDeckPage page(StoredDeckPage stored) {
		return new RecommendationDeckPage(
			stored.deckPublicId(),
			stored.scope(),
			new LocationSummary(
				stored.originLocationId(),
				stored.originDisplayName(),
				stored.originServiceRegionCode()),
			stored.cards().stream().map(RecommendationDeckService::card).toList(),
			stored.nextCursor(),
			stored.hasMore(),
			REMAINING_THRESHOLD,
			new Deduplication(true, stored.suppressionDays()));
	}

	private static RecommendationCard card(CardSnapshot snapshot) {
		return new RecommendationCard(
			snapshot.placeId(),
			snapshot.title(),
			snapshot.locationText(),
			snapshot.imageUrl(),
			false,
			snapshot.tags(),
			snapshot.shortDescription(),
			snapshot.serviceRegionCode(),
			snapshot.travelStyle(),
			snapshot.matchRank(),
			new MatchReason(
				snapshot.travelStyleMatched(),
				snapshot.preferenceTagMatched(),
				snapshot.matchedTagCodes()));
	}

	private static String shortDescription(String overview) {
		if (overview == null || overview.isBlank()) {
			return null;
		}
		String normalized = overview.strip().replaceAll("\\s+", " ");
		if (normalized.length() <= SHORT_DESCRIPTION_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, SHORT_DESCRIPTION_LENGTH - 3).stripTrailing() + "...";
	}

	private static String tieBreak(String seed, long placeId) {
		return sha256(seed + ":" + placeId);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record RecommendationDeckPage(
		String deckId,
		RecommendationScope scope,
		LocationSummary originLocation,
		List<RecommendationCard> cards,
		String nextCursor,
		boolean hasMore,
		int remainingThreshold,
		Deduplication deduplication
	) {
	}

	public record LocationSummary(
		long locationId,
		String displayName,
		ServiceRegionCode serviceRegionCode
	) {
	}

	public record RecommendationCard(
		long placeId,
		String title,
		String locationText,
		String imageUrl,
		boolean saved,
		List<String> tags,
		String shortDescription,
		ServiceRegionCode serviceRegionCode,
		TravelStyle travelStyle,
		int matchRank,
		MatchReason matchReason
	) {
	}

	public record MatchReason(
		boolean travelStyleMatched,
		boolean preferenceTagMatched,
		List<String> matchedTagCodes
	) {
	}

	public record Deduplication(
		boolean guaranteedWithinDeck,
		int suppressionDays
	) {
	}
}
