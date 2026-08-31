package koready_backend.route.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class RouteCandidateSelector {

	private RouteCandidateSelector() {
	}

	public static Optional<RouteCandidate> select(
		List<RouteCandidate> candidates
	) {
		return candidates.stream()
			.filter(RouteCandidate::serviceAvailable)
			.min(Comparator.comparingInt(RouteCandidate::totalTimeSeconds)
				.thenComparingInt(RouteCandidate::transferCount)
				.thenComparingInt(RouteCandidate::totalWalkDistanceMeters));
	}
}
