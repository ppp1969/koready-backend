package koready_backend.batch.infrastructure.scheduling;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.continuous-sync.schedule")
public record KtoDailySourceSyncScheduleProperties(
	String zone
) {
	public KtoDailySourceSyncScheduleProperties {
		ZoneId.of(zone);
	}

	ZoneId zoneId() {
		return ZoneId.of(zone);
	}
}
