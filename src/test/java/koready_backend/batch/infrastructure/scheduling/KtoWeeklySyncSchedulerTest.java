package koready_backend.batch.infrastructure.scheduling;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import koready_backend.batch.application.BatchJobCommandService;

@ExtendWith(MockitoExtension.class)
class KtoWeeklySyncSchedulerTest {

	@Mock
	BatchJobCommandService commandService;

	@Test
	void springSelectsTheRuntimeConstructorWhenTheSchedulerIsEnabled() {
		try (var context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("test", java.util.Map.of(
					"koready.kto.continuous-sync.schedule.enabled", "true")));
			context.registerBean(BatchJobCommandService.class, () -> commandService);
			context.registerBean(
				KtoWeeklySyncScheduleProperties.class,
				() -> new KtoWeeklySyncScheduleProperties(800, "Asia/Seoul"));
			context.register(KtoWeeklySyncScheduler.class);

			context.refresh();

			context.getBean(KtoWeeklySyncScheduler.class);
		}
	}

	@Test
	void schedulesTheWeeklyPipelineWithTheConfiguredBudget() {
		LocalDate date = LocalDate.parse("2026-09-13");
		when(commandService.scheduleWeeklyKtoSync(date, 800))
			.thenReturn(new BatchJobCommandService.DailyScheduleResult(true, 1L));
		var scheduler = new KtoWeeklySyncScheduler(
			commandService,
			new KtoWeeklySyncScheduleProperties(800, "Asia/Seoul"),
			Clock.fixed(Instant.parse("2026-09-12T18:05:00Z"), ZoneOffset.UTC));

		scheduler.schedule();

		verify(commandService).scheduleWeeklyKtoSync(date, 800);
	}
}
