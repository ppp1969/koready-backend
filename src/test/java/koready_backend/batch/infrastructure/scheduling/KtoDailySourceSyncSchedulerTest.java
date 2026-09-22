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
class KtoDailySourceSyncSchedulerTest {

	@Mock
	BatchJobCommandService commandService;

	@Test
	void doesNotStartBeforeTheProviderDailyUpdateHasFinished() {
		var scheduler = new KtoDailySourceSyncScheduler(
			commandService, new KtoDailySourceSyncScheduleProperties("Asia/Seoul"),
			Clock.fixed(Instant.parse("2026-09-21T22:59:00Z"), ZoneOffset.UTC));
		org.junit.jupiter.api.Assertions.assertDoesNotThrow(scheduler::schedule);
		org.mockito.Mockito.verifyNoInteractions(commandService);
	}

	@Test
	void springSelectsTheRuntimeConstructorWhenTheSchedulerIsEnabled() {
		try (var context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("test", java.util.Map.of(
					"koready.kto.continuous-sync.schedule.enabled", "true")));
			context.registerBean(BatchJobCommandService.class, () -> commandService);
			context.registerBean(
				KtoDailySourceSyncScheduleProperties.class,
				() -> new KtoDailySourceSyncScheduleProperties("Asia/Seoul"));
			context.register(KtoDailySourceSyncScheduler.class);

			context.refresh();

			context.getBean(KtoDailySourceSyncScheduler.class);
		}
	}

	@Test
	void schedulesTheDailyPipelineAfterEightKst() {
		LocalDate date = LocalDate.parse("2026-09-13");
		when(commandService.scheduleDailyKtoSync(date))
			.thenReturn(new BatchJobCommandService.DailyScheduleResult(true, 1L));
		var scheduler = new KtoDailySourceSyncScheduler(
			commandService,
			new KtoDailySourceSyncScheduleProperties("Asia/Seoul"),
			Clock.fixed(Instant.parse("2026-09-13T00:05:00Z"), ZoneOffset.UTC));

		scheduler.schedule();

		verify(commandService).scheduleDailyKtoSync(date);
	}
}
