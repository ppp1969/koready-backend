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

import koready_backend.batch.application.BatchJobCommandService;

@ExtendWith(MockitoExtension.class)
class KtoWeeklySyncSchedulerTest {

	@Mock
	BatchJobCommandService commandService;

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
