package koready_backend.batch.infrastructure.scheduling;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
	prefix = "koready.kto.detail-enrichment.schedule")
public record KtoDetailDailyScheduleProperties(
	int dailyPlaces,
	int chunkPlaces,
	String zone
) {

	private static final int MAX_DAILY_PLACES = 900;
	private static final int MAX_CHUNK_PLACES = 50;

	public KtoDetailDailyScheduleProperties {
		if (dailyPlaces < 1 || dailyPlaces > MAX_DAILY_PLACES) {
			throw new IllegalArgumentException(
				"KTO daily detail places must be between 1 and 900");
		}
		if (chunkPlaces < 1 || chunkPlaces > MAX_CHUNK_PLACES
			|| chunkPlaces > dailyPlaces) {
			throw new IllegalArgumentException(
				"KTO daily detail chunk places must be between 1 and 50 and not exceed the daily budget");
		}
		if (zone == null || zone.isBlank()) {
			throw new IllegalArgumentException(
				"KTO daily detail schedule zone is required");
		}
		ZoneId.of(zone);
	}

	ZoneId zoneId() {
		return ZoneId.of(zone);
	}
}
