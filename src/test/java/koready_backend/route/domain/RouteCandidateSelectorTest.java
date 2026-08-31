package koready_backend.route.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RouteCandidateSelectorTest {

	@Test
	void selectsOnlyServiceAvailableCandidateBeforeApplyingRanking() {
		var unavailableFast = candidate(900, 0, 100, false);
		var availableSlow = candidate(1200, 1, 300, true);

		var selected = RouteCandidateSelector.select(
			List.of(unavailableFast, availableSlow));

		assertThat(selected).contains(availableSlow);
	}

	@Test
	void ranksAvailableCandidatesByTimeTransferAndWalkingDistance() {
		var moreTransfers = candidate(1200, 2, 100, true);
		var lessTransfers = candidate(1200, 1, 300, true);
		var lessWalking = candidate(1200, 1, 200, true);

		var selected = RouteCandidateSelector.select(
			List.of(moreTransfers, lessTransfers, lessWalking));

		assertThat(selected).contains(lessWalking);
	}

	private static RouteCandidate candidate(
		int totalSeconds,
		int transferCount,
		int walkDistance,
		boolean serviceAvailable
	) {
		return new RouteCandidate(
			totalSeconds,
			300,
			walkDistance,
			transferCount,
			1000,
			List.of(new RouteCandidate.RouteLeg(
				RouteMode.BUS, "출발", "도착", "버스", 300, 1000,
				null, serviceAvailable)));
	}
}
