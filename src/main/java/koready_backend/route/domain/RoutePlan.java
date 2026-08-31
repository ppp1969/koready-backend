package koready_backend.route.domain;

import java.time.Instant;
import java.util.List;

public record RoutePlan(
	String routeId,
	long destinationPlaceId,
	String language,
	RoutePoint origin,
	RoutePoint destination,
	Instant fetchedAt,
	Instant expiresAt,
	int providerTotalTimeSeconds,
	RouteSummary summary,
	List<RouteSegment> segments
) {
	public RoutePlan {
		segments = List.copyOf(segments);
	}

	public record RoutePoint(String name, String address) {
	}

	public record RouteSummary(
		String recommendedTransportText,
		int estimatedOneWayMinutes,
		String estimatedOneWayTimeText,
		int transferCount,
		int totalWalkDistanceMeters,
		int totalWalkMinutes,
		RoutePolicy.Difficulty difficulty,
		DayTripStatus dayTripStatus,
		RouteFare fare,
		List<RouteMode> transportModes
	) {
		public RouteSummary {
			transportModes = List.copyOf(transportModes);
		}
	}

	public record RouteFare(
		Integer oneWayEstimated,
		Integer roundTripEstimated,
		String coverage
	) {
	}

	public record RouteSegment(
		int order,
		String startName,
		String endName,
		RouteMode mode,
		String routeName,
		int durationMinutes,
		int distanceMeters,
		Integer fare,
		boolean serviceAvailable,
		String instruction
	) {
	}
}
