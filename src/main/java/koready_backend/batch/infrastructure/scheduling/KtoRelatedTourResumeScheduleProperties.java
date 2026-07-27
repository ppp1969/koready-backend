package koready_backend.batch.infrastructure.scheduling;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
	prefix = "koready.kto.related-tour-resume.schedule")
public record KtoRelatedTourResumeScheduleProperties(
	String zone
) {

	public KtoRelatedTourResumeScheduleProperties {
		if (zone == null || zone.isBlank()) {
			throw new IllegalArgumentException(
				"KTO related tour resume schedule zone is required");
		}
		ZoneId.of(zone);
	}

	ZoneId zoneId() {
		return ZoneId.of(zone);
	}
}
