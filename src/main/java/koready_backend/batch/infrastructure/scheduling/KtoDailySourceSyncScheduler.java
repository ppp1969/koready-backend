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
	prefix = "koready.kto.continuous-sync.schedule",
	name = "enabled",
	havingValue = "true")
public class KtoDailySourceSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(KtoDailySourceSyncScheduler.class);

	private final BatchJobCommandService commandService;
	private final KtoDailySourceSyncScheduleProperties properties;
	private final Clock clock;

	@Autowired
	public KtoDailySourceSyncScheduler(
		BatchJobCommandService commandService,
		KtoDailySourceSyncScheduleProperties properties
	) {
		this(commandService, properties, Clock.system(properties.zoneId()));
	}

	KtoDailySourceSyncScheduler(
		BatchJobCommandService commandService,
		KtoDailySourceSyncScheduleProperties properties,
		Clock clock
	) {
		this.commandService = commandService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(
		cron = "${koready.kto.continuous-sync.schedule.cron:0 */10 8-23 * * *}",
		zone = "${koready.kto.continuous-sync.schedule.zone:Asia/Seoul}")
	public void schedule() {
		if (java.time.LocalTime.now(clock.withZone(properties.zoneId())).getHour() < 8) { return; }
		LocalDate date = LocalDate.now(clock.withZone(properties.zoneId()));
		var result = commandService.scheduleDailyKtoSync(date);
		if (result.scheduled()) {
			log.info("Scheduled daily KTO synchronization stage. jobId={}, date={}", result.jobId(), date);
		}
	}
}
