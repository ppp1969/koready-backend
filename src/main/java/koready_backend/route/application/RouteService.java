package koready_backend.route.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.horitip.application.HoriTipService;
import koready_backend.horitip.domain.HoriTipPlacement;
import koready_backend.horitip.domain.HoriTipRouteMode;
import koready_backend.horitip.domain.HoriTipTranslation;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.route.application.exception.RouteException;
import koready_backend.route.application.exception.TransitProviderException;
import koready_backend.route.application.port.RouteRepository;
import koready_backend.route.application.port.TransitRouteProvider;
import koready_backend.route.domain.DayTripStatus;
import koready_backend.route.domain.RouteCandidate;
import koready_backend.route.domain.RouteCandidateSelector;
import koready_backend.route.domain.RouteMode;
import koready_backend.route.domain.RoutePlan;
import koready_backend.route.domain.RoutePolicy;

@Service
public class RouteService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Duration CACHE_TTL = Duration.ofMinutes(30);

	private final RouteRepository repository;
	private final TransitRouteProvider provider;
	private final HoriTipService horiTipService;
	private final Clock clock;

	@Autowired
	public RouteService(
		RouteRepository repository,
		TransitRouteProvider provider,
		HoriTipService horiTipService
	) {
		this(repository, provider, horiTipService, Clock.systemUTC());
	}

	RouteService(
		RouteRepository repository,
		TransitRouteProvider provider,
		HoriTipService horiTipService,
		Clock clock
	) {
		this.repository = repository;
		this.provider = provider;
		this.horiTipService = horiTipService;
		this.clock = clock;
	}

	@Transactional
	public RouteView create(String subject, CreateCommand command) {
		var context = repository.findContext(
			subject, command.originLocationId(), command.destinationPlaceId())
			.orElseThrow(() -> new RouteException(RouteException.Reason.CONTEXT_NOT_FOUND));
		Instant now = clock.instant();
		ZonedDateTime departure = command.departureAt() == null
			? now.atZone(SEOUL)
			: command.departureAt().withZoneSameInstant(SEOUL);

		List<RouteCandidate> candidates;
		try {
			candidates = provider.findRoutes(new TransitRouteProvider.RouteProviderRequest(
				context.originLatitude(), context.originLongitude(),
				context.destinationLatitude(), context.destinationLongitude(), departure));
		} catch (TransitProviderException exception) {
			throw new RouteException(RouteException.Reason.ROUTE_PROVIDER_UNAVAILABLE);
		}
		if (candidates.isEmpty()) {
			throw new RouteException(RouteException.Reason.ROUTE_NOT_FOUND);
		}
		var selected = RouteCandidateSelector.select(candidates)
			.orElseThrow(() -> new RouteException(
				RouteException.Reason.ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME));
		RoutePlan route = normalize(command.destinationPlaceId(), context, selected, now);
		repository.save(context.userId(), route);
		return enrich(route);
	}

	@Transactional(readOnly = true)
	public RouteView get(String subject, String routeId) {
		RoutePlan route = repository.findOwned(routeId, subject)
			.orElseThrow(() -> new RouteException(RouteException.Reason.ROUTE_EXPIRED));
		if (!route.expiresAt().isAfter(clock.instant())) {
			throw new RouteException(RouteException.Reason.ROUTE_EXPIRED);
		}
		return enrich(route);
	}

	private RoutePlan normalize(
		long destinationPlaceId,
		RouteRepository.RouteContext context,
		RouteCandidate candidate,
		Instant now
	) {
		List<RoutePlan.RouteSegment> segments = new ArrayList<>();
		int order = 1;
		for (var leg : candidate.legs()) {
			segments.add(new RoutePlan.RouteSegment(
				order++, leg.startName(), leg.endName(), leg.mode(), leg.routeName(),
				RoutePolicy.minutes(leg.durationSeconds()), leg.distanceMeters(), leg.fare(),
				leg.mode() == RouteMode.WALK || leg.serviceAvailable(),
				instruction(leg.mode(), leg.routeName(), context.language())));
		}
		List<RouteMode> modes = List.copyOf(new LinkedHashSet<>(candidate.legs().stream()
			.map(RouteCandidate.RouteLeg::mode).toList()));
		Integer fare = candidate.totalFare();
		RoutePlan.RouteSummary summary = new RoutePlan.RouteSummary(
			transportText(modes, context.language()),
			RoutePolicy.minutes(candidate.totalTimeSeconds()),
			timeText(candidate.totalTimeSeconds(), context.language()),
			candidate.transferCount(), candidate.totalWalkDistanceMeters(),
			RoutePolicy.minutes(candidate.totalWalkTimeSeconds()),
			RoutePolicy.difficulty(candidate.totalTimeSeconds(), candidate.transferCount(),
				candidate.totalWalkDistanceMeters()),
			RoutePolicy.dayTripStatus(candidate.totalTimeSeconds()),
			new RoutePlan.RouteFare(fare, fare == null ? null : fare * 2,
				fare == null ? "UNAVAILABLE" : "AVAILABLE_SEGMENTS_ONLY"),
			modes);
		return new RoutePlan(
			"route_" + UUID.randomUUID().toString().replace("-", ""),
			destinationPlaceId,
			context.language(),
			new RoutePlan.RoutePoint(context.originName(), context.originAddress()),
			new RoutePlan.RoutePoint(context.destinationName(), context.destinationAddress()),
			now, now.plus(CACHE_TTL), candidate.totalTimeSeconds(), summary, segments);
	}

	private RouteView enrich(RoutePlan route) {
		List<HoriTipView> summaryTips = new ArrayList<>();
		List<SegmentView> segments = route.segments().stream()
			.map(segment -> new SegmentView(segment, new ArrayList<>())).toList();
		for (var tip : horiTipService.findActiveRouteTips(route.destinationPlaceId())) {
			if (!matchesSummary(tip.draft().trigger(), route)) {
				continue;
			}
			String body = body(tip.draft().translations(), route.language());
			if (body == null) {
				continue;
			}
			var view = new HoriTipView(tip.code(), body, tip.draft().placement());
			if (tip.draft().placement() == HoriTipPlacement.TOP_SUMMARY) {
				summaryTips.add(view);
			} else {
				segments.stream().filter(segment -> matchesSegment(
					tip.draft().trigger(), segment.segment())).findFirst()
					.ifPresent(segment -> segment.horiTips().add(view));
			}
		}
		return new RouteView(route, List.copyOf(summaryTips), segments.stream()
			.map(segment -> new SegmentView(segment.segment(), List.copyOf(segment.horiTips())))
			.toList());
	}

	private static boolean matchesSummary(
		koready_backend.horitip.domain.HoriTipTrigger trigger,
		RoutePlan route
	) {
		boolean thresholdsMatch = (trigger.minProviderTotalTimeSeconds() == null
			|| route.providerTotalTimeSeconds() >= trigger.minProviderTotalTimeSeconds())
			&& (trigger.minTransferCount() == null
			|| route.summary().transferCount() >= trigger.minTransferCount())
			&& (trigger.minTotalWalkDistanceMeters() == null
			|| route.summary().totalWalkDistanceMeters()
				>= trigger.minTotalWalkDistanceMeters());
		return thresholdsMatch && (!trigger.hasSegmentCondition()
			|| route.segments().stream().anyMatch(segment -> matchesSegment(trigger, segment)));
	}

	private static boolean matchesSegment(
		koready_backend.horitip.domain.HoriTipTrigger trigger,
		RoutePlan.RouteSegment segment
	) {
		if (!trigger.segmentModes().isEmpty()
			&& !trigger.segmentModes().contains(HoriTipRouteMode.valueOf(segment.mode().name()))) {
			return false;
		}
		return containsAny(segment.routeName(), trigger.routeNameContainsAny())
			&& containsAny(segment.startName(), trigger.segmentStartNameContainsAny())
			&& containsAny(segment.endName(), trigger.segmentEndNameContainsAny());
	}

	private static boolean containsAny(String value, List<String> needles) {
		if (needles.isEmpty()) {
			return true;
		}
		String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
		return needles.stream().anyMatch(needle ->
			normalized.contains(needle.toLowerCase(Locale.ROOT)));
	}

	private static String body(List<HoriTipTranslation> translations, String language) {
		PlaceLanguage wanted = PlaceLanguage.valueOf(language);
		return translations.stream().filter(item -> item.language() == wanted)
			.map(HoriTipTranslation::body).findFirst().orElse(null);
	}

	private static String instruction(RouteMode mode, String routeName, String language) {
		boolean english = "EN".equals(language);
		if (mode == RouteMode.WALK) {
			return english ? "Continue on foot." : "도보로 이동하세요.";
		}
		String name = routeName == null || routeName.isBlank()
			? (english ? mode.name() : mode.name()) : routeName;
		return english ? "Take " + name + "." : name + "을(를) 이용하세요.";
	}

	private static String transportText(List<RouteMode> modes, String language) {
		return modes.stream().map(mode -> displayMode(mode, language))
			.distinct().reduce((left, right) -> left + " + " + right).orElse("");
	}

	private static String displayMode(RouteMode mode, String language) {
		if ("EN".equals(language)) {
			return mode.name().replace('_', ' ');
		}
		return switch (mode) {
			case WALK -> "도보";
			case BUS -> "버스";
			case SUBWAY -> "지하철";
			case EXPRESS_BUS -> "고속버스";
			case TRAIN -> "기차";
			case AIRPLANE -> "항공";
			case FERRY -> "선박";
			case SHUTTLE_BUS -> "셔틀버스";
		};
	}

	private static String timeText(int seconds, String language) {
		int minutes = RoutePolicy.minutes(seconds);
		int hours = minutes / 60;
		int rest = minutes % 60;
		if ("EN".equals(language)) {
			return hours == 0 ? "About " + rest + " min"
				: "About " + hours + " hr " + rest + " min";
		}
		return hours == 0 ? "약 " + rest + "분"
			: "약 " + hours + "시간 " + rest + "분";
	}

	public record CreateCommand(long originLocationId, long destinationPlaceId,
		ZonedDateTime departureAt) {
	}

	public record HoriTipView(String code, String body, HoriTipPlacement placement) {
	}

	public record SegmentView(RoutePlan.RouteSegment segment, List<HoriTipView> horiTips) {
	}

	public record RouteView(RoutePlan route, List<HoriTipView> summaryHoriTips,
		List<SegmentView> segments) {
	}
}
