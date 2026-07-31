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
	prefix = "koready.kto.detail-enrichment.schedule",
	name = "enabled",
	havingValue = "true")
public class KtoDetailDailyScheduler {

	private static final Logger log =
		LoggerFactory.getLogger(KtoDetailDailyScheduler.class);

	private final BatchJobCommandService commandService;
	private final KtoDetailDailyScheduleProperties properties;
	private final Clock clock;

	@Autowired
	public KtoDetailDailyScheduler(
		BatchJobCommandService commandService,
		KtoDetailDailyScheduleProperties properties
	) {
		this(
			commandService,
			properties,
			Clock.system(properties.zoneId()));
	}

	KtoDetailDailyScheduler(
		BatchJobCommandService commandService,
		KtoDetailDailyScheduleProperties properties,
		Clock clock
	) {
		this.commandService = commandService;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(
		cron = "${koready.kto.detail-enrichment.schedule.cron:0 */30 * * * *}",
		zone = "${koready.kto.detail-enrichment.schedule.zone:Asia/Seoul}")
	public void schedule() {
		LocalDate scheduleDate = LocalDate.now(
			clock.withZone(properties.zoneId()));
		var result = commandService.scheduleDailyDetail(
			scheduleDate,
			properties.dailyPlaces(),
			properties.chunkPlaces());
		if (result.scheduled()) {
			log.info(
				"Scheduled daily KTO detail budget. jobId={}, scheduleDate={}, dailyPlaces={}, chunkPlaces={}",
				result.jobId(), scheduleDate, properties.dailyPlaces(), properties.chunkPlaces());
		}
	}
}
