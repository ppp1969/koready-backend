package koready_backend.route.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutePolicyTest {

	@Test
	void usesProviderSecondsForDayTripBoundary() {
		assertThat(RoutePolicy.dayTripStatus(10_799))
			.isEqualTo(DayTripStatus.DAY_TRIP_AVAILABLE);
		assertThat(RoutePolicy.dayTripStatus(10_800))
			.isEqualTo(DayTripStatus.STAY_RECOMMENDED);
	}

	@Test
	void roundsDisplayedMinutesUp() {
		assertThat(RoutePolicy.minutes(61)).isEqualTo(2);
	}
}
