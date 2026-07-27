package koready_backend.batch.infrastructure.scheduling;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
	prefix = "koready.kto.detail-enrichment.schedule")
public record KtoDetailDailyScheduleProperties(
	int maxPlaces,
	String zone
) {

	private static final int MAX_PLACES = 50;

	public KtoDetailDailyScheduleProperties {
		if (maxPlaces < 1 || maxPlaces > MAX_PLACES) {
			throw new IllegalArgumentException(
				"KTO daily detail max places must be between 1 and 50");
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
