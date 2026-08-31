package koready_backend.route.domain;

import java.util.List;

public record RouteCandidate(
	int totalTimeSeconds,
	int totalWalkTimeSeconds,
	int totalWalkDistanceMeters,
	int transferCount,
	Integer totalFare,
	List<RouteLeg> legs
) {
	public RouteCandidate {
		legs = List.copyOf(legs);
	}

	public boolean serviceAvailable() {
		return legs.stream().allMatch(leg ->
			leg.mode() == RouteMode.WALK || leg.serviceAvailable());
	}

	public record RouteLeg(
		RouteMode mode,
		String startName,
		String endName,
		String routeName,
		int durationSeconds,
		int distanceMeters,
		Integer fare,
		boolean serviceAvailable
	) {
	}
}
