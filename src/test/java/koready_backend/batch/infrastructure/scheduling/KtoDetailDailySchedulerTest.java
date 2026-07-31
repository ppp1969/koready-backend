package koready_backend.batch.infrastructure.scheduling;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.batch.application.BatchJobCommandService;

@ExtendWith(MockitoExtension.class)
class KtoDetailDailySchedulerTest {

	@Mock
	BatchJobCommandService commandService;

	@Test
	void schedulesTheKoreanBusinessDateWithTheConfiguredBudget() {
		when(commandService.scheduleDailyDetail(
			java.time.LocalDate.parse("2026-07-20"), 800, 50))
			.thenReturn(
				new BatchJobCommandService.DailyScheduleResult(
					false, null));
		var scheduler = new KtoDetailDailyScheduler(
			commandService,
			new KtoDetailDailyScheduleProperties(
				800, 50, "Asia/Seoul"),
			Clock.fixed(
				Instant.parse("2026-07-19T15:30:00Z"),
				ZoneOffset.UTC));

		scheduler.schedule();

		verify(commandService).scheduleDailyDetail(
			java.time.LocalDate.parse("2026-07-20"), 800, 50);
	}
}
