package koready_backend.route.domain;

public final class RoutePolicy {

	private static final int STAY_RECOMMENDED_SECONDS = 10_800;

	private RoutePolicy() {
	}

	public static int minutes(int seconds) {
		return Math.max(0, (seconds + 59) / 60);
	}

	public static DayTripStatus dayTripStatus(int providerTotalTimeSeconds) {
		return providerTotalTimeSeconds < STAY_RECOMMENDED_SECONDS
			? DayTripStatus.DAY_TRIP_AVAILABLE
			: DayTripStatus.STAY_RECOMMENDED;
	}

	public static Difficulty difficulty(int totalSeconds, int transfers, int walkMeters) {
		if (totalSeconds >= 10_800 || transfers >= 4 || walkMeters >= 2_000) {
			return Difficulty.HARD;
		}
		if (totalSeconds >= 5_400 || transfers >= 2 || walkMeters >= 1_000) {
			return Difficulty.NORMAL;
		}
		return Difficulty.EASY;
	}

	public enum Difficulty {
		EASY,
		NORMAL,
		HARD
	}
}
