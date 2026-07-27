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
class KtoRelatedTourResumeSchedulerTest {

	@Mock
	BatchJobCommandService commandService;

	@Test
	void resumesAgainstTheKoreanBusinessDate() {
		LocalDate scheduleDate = LocalDate.parse("2026-07-21");
		when(commandService.scheduleDailyRelatedTourResume(scheduleDate))
			.thenReturn(
				new BatchJobCommandService.DailyScheduleResult(
					false, null));
		var scheduler = new KtoRelatedTourResumeScheduler(
			commandService,
			new KtoRelatedTourResumeScheduleProperties(
				"Asia/Seoul"),
			Clock.fixed(
				Instant.parse("2026-07-20T15:15:00Z"),
				ZoneOffset.UTC));

		scheduler.schedule();

		verify(commandService)
			.scheduleDailyRelatedTourResume(scheduleDate);
	}
}
