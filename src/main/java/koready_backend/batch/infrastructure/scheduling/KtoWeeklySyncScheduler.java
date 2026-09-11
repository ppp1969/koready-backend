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
public class KtoWeeklySyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(KtoWeeklySyncScheduler.class);

	private final BatchJobCommandService commandService;
	private final KtoWeeklySyncScheduleProperties properties;
	private final Clock clock;

	@Autowired
	public KtoWeeklySyncScheduler(
		BatchJobCommandService commandService,
		KtoWeeklySyncScheduleProperties properties
	) {
		this(commandService, properties, Clock.system(properties.zoneId()));
	}

	KtoWeeklySyncScheduler(
		BatchJobCommandService commandService,
		KtoWeeklySyncScheduleProperties properties,
		Clock clock
	) {
		this.commandService = commandService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(
		cron = "${koready.kto.continuous-sync.schedule.cron:0 */10 3-23 * * SUN}",
		zone = "${koready.kto.continuous-sync.schedule.zone:Asia/Seoul}")
	public void schedule() {
		LocalDate date = LocalDate.now(clock.withZone(properties.zoneId()));
		var result = commandService.scheduleWeeklyKtoSync(date, properties.requestBudget());
		if (result.scheduled()) {
			log.info("Scheduled weekly KTO synchronization stage. jobId={}, date={}, requestBudget={}",
				result.jobId(), date, properties.requestBudget());
		}
	}
}
