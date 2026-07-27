package koready_backend.batch.infrastructure.scheduling;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import koready_backend.batch.application.BatchJobCommandService;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.related-tour-resume.schedule",
	name = "enabled",
	havingValue = "true")
public class KtoRelatedTourResumeScheduler {

	private static final Logger log =
		LoggerFactory.getLogger(
			KtoRelatedTourResumeScheduler.class);

	private final BatchJobCommandService commandService;
	private final KtoRelatedTourResumeScheduleProperties properties;
	private final Clock clock;

	@Autowired
	public KtoRelatedTourResumeScheduler(
		BatchJobCommandService commandService,
		KtoRelatedTourResumeScheduleProperties properties
	) {
		this(
			commandService,
			properties,
			Clock.system(properties.zoneId()));
	}

	KtoRelatedTourResumeScheduler(
		BatchJobCommandService commandService,
		KtoRelatedTourResumeScheduleProperties properties,
		Clock clock
	) {
		this.commandService = commandService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(
		cron = "${koready.kto.related-tour-resume.schedule.cron:0 15 0 * * *}",
		zone = "${koready.kto.related-tour-resume.schedule.zone:Asia/Seoul}")
	public void schedule() {
		LocalDate scheduleDate = LocalDate.now(
			clock.withZone(properties.zoneId()));
		var result =
			commandService.scheduleDailyRelatedTourResume(
				scheduleDate);
		if (result.scheduled()) {
			log.info(
				"Scheduled KTO related tour resume. jobId={}, scheduleDate={}",
				result.jobId(),
				scheduleDate);
		}
	}
}
