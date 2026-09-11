package koready_backend.batch.infrastructure.scheduling;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.continuous-sync.schedule")
public record KtoWeeklySyncScheduleProperties(
	int requestBudget,
	String zone
) {
	public KtoWeeklySyncScheduleProperties {
		if (requestBudget < 1 || requestBudget > 100_000) {
			throw new IllegalArgumentException("KTO weekly request budget must be between 1 and 100000");
		}
		ZoneId.of(zone);
	}

	ZoneId zoneId() {
		return ZoneId.of(zone);
	}
}
