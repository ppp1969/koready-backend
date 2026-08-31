package koready_backend.route.controller;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.Positive;
import koready_backend.route.application.RouteService;
import koready_backend.route.domain.RouteMode;
import koready_backend.route.domain.RoutePlan;

final class RouteDtos {

	private static final String FARE_DISCLAIMER =
		"실제 요금과 다를 수 있으며 일부 셔틀 비용은 제외될 수 있습니다.";

	private RouteDtos() {
	}

	static RouteResponse from(RouteService.RouteView view) {
		RoutePlan route = view.route();
		var summary = route.summary();
		return new RouteResponse(
			route.routeId(), "TMAP_TRANSIT",
			new RoutePoint(route.origin().name(), route.origin().address()),
			new RoutePoint(route.destination().name(), route.destination().address()),
			route.fetchedAt().toString(), route.expiresAt().toString(),
			new RouteSummary(
				summary.recommendedTransportText(), summary.estimatedOneWayMinutes(),
				summary.estimatedOneWayTimeText(), summary.transferCount(),
				summary.totalWalkDistanceMeters(), summary.totalWalkMinutes(),
				summary.difficulty().name(), "route-difficulty-v1",
				summary.dayTripStatus().name(),
				new RouteFare(summary.fare().oneWayEstimated(),
					summary.fare().roundTripEstimated(), "KRW",
					summary.fare().coverage(), FARE_DISCLAIMER),
				summary.transportModes(), tips(view.summaryHoriTips())),
			view.segments().stream().map(segment -> {
				var item = segment.segment();
				return new RouteSegment(
					item.order(), "TMAP", item.startName(), item.endName(), item.mode(),
					item.routeName(), item.durationMinutes(), item.distanceMeters(),
					item.fare(), item.instruction(), item.serviceAvailable(),
					tips(segment.horiTips()));
			}).toList(),
			List.of(), true);
	}

	private static List<HoriTip> tips(List<RouteService.HoriTipView> tips) {
		return tips.stream().map(tip -> new HoriTip(
			tip.code(), "OPERATOR_CURATED", "Hori Tip", tip.body(),
			tip.placement().name())).toList();
	}

	record RouteRequest(
		@Positive long originLocationId,
		@Positive long destinationPlaceId,
		ZonedDateTime departureAt
	) {
		RouteService.CreateCommand toCommand() {
			return new RouteService.CreateCommand(
				originLocationId, destinationPlaceId, departureAt);
		}
	}

	record RouteResponse(
		String routeId,
		String provider,
		RoutePoint origin,
		RoutePoint destination,
		String fetchedAt,
		String expiresAt,
		RouteSummary summary,
		List<RouteSegment> segments,
		List<RouteWarning> warnings,
		boolean detailAvailable
	) {
	}

	record RoutePoint(String name, String address) {
	}

	record RouteSummary(
		String recommendedTransportText,
		int estimatedOneWayMinutes,
		String estimatedOneWayTimeText,
		int transferCount,
		int totalWalkDistanceMeters,
		int totalWalkMinutes,
		String difficulty,
		String difficultyAlgorithmVersion,
		String dayTripStatus,
		RouteFare fare,
		List<RouteMode> transportModes,
		List<HoriTip> horiTips
	) {
	}

	record RouteFare(
		Integer oneWayEstimated,
		Integer roundTripEstimated,
		String currencyCode,
		String coverage,
		String disclaimer
	) {
	}

	record RouteSegment(
		int order,
		String source,
		String startName,
		String endName,
		RouteMode mode,
		String routeName,
		int durationMinutes,
		int distanceMeters,
		Integer fare,
		String instruction,
		boolean serviceAvailable,
		List<HoriTip> horiTips
	) {
	}

	record HoriTip(String code, String source, String title, String body, String placement) {
	}

	record RouteWarning(String code, String message, Integer segmentOrder) {
	}
}
