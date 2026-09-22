package koready_backend.batch.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.*;
import koready_backend.batch.domain.*;

class DailyKtoSyncTest {
	private static final LocalDate DAY = LocalDate.of(2026, 9, 22);
	private String key(BatchJobType type) { return "KTO_DAILY_SOURCE:" + DAY + ":" + type; }

	@Test
	void runsEachSourceOnceAndDoesNotUseTheOld800PageOrPlaceCap() {
		var repository = mock(BatchJobCommandRepository.class);
		var states = new HashMap<String, MaintenanceStageState>();
		when(repository.findMaintenanceStageState(anyString())).thenAnswer(call ->
			states.getOrDefault(call.getArgument(0), MaintenanceStageState.NOT_STARTED));
		when(repository.enqueue(any())).thenAnswer(call -> {
			EnqueueCommand command = call.getArgument(0);
			states.put(command.scheduleKey(), MaintenanceStageState.COMPLETED);
			assertFalse(command.parameters().containsKey("remainingPages"));
			assertFalse(command.parameters().containsKey("remainingDailyPlaces"));
			return (long) states.size();
		});
		var service = new BatchJobCommandService(repository);
		for (int i = 0; i < 7; i++) { assertTrue(service.scheduleDailyKtoSync(DAY).scheduled()); }
		assertFalse(service.scheduleDailyKtoSync(DAY).scheduled());
		var commands = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository, times(7)).enqueue(commands.capture());
		assertEquals(7, commands.getAllValues().stream().map(EnqueueCommand::jobType).distinct().count());
		assertTrue(service.scheduleDailyKtoSync(DAY.plusDays(1)).scheduled());
	}

	@Test
	void resumesTheLastFailedChunkOnTheNextDayAndLetsOtherOperationsRunAfterQuotaFailure() {
		var repository = mock(BatchJobCommandRepository.class);
		when(repository.findMaintenanceStageState(anyString())).thenReturn(MaintenanceStageState.NOT_STARTED);
		when(repository.findMaintenanceStageState(key(BatchJobType.KTO_FULL_CATALOG_SYNC)))
			.thenReturn(MaintenanceStageState.FAILED);
		var parameters = Map.<String, Object>of("startPage", 81, "maxPages", 20);
		when(repository.findLatestDailySyncSource(BatchJobType.KTO_EN_SYNC)).thenReturn(Optional.of(
			new RetrySource(123, BatchJobType.KTO_EN_SYNC, BatchJobStatus.FAILED, parameters)));
		when(repository.enqueue(any())).thenReturn(124L);
		assertTrue(new BatchJobCommandService(repository).scheduleDailyKtoSync(DAY).scheduled());
		var command = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(command.capture());
		assertEquals(BatchJobType.KTO_EN_SYNC, command.getValue().jobType());
		assertEquals(parameters, command.getValue().parameters());
		assertNull(command.getValue().parentJobId());
	}

	@Test
	void waitsForAnActiveStageAndToleratesACompetingManualJob() {
		var repository = mock(BatchJobCommandRepository.class);
		when(repository.findMaintenanceStageState(anyString())).thenReturn(MaintenanceStageState.IN_PROGRESS);
		var service = new BatchJobCommandService(repository);
		assertFalse(service.scheduleDailyKtoSync(DAY).scheduled());
		verify(repository, never()).enqueue(any());
		when(repository.findMaintenanceStageState(anyString())).thenReturn(MaintenanceStageState.NOT_STARTED);
		when(repository.enqueue(any())).thenThrow(new DuplicateKeyException("active slot"));
		assertFalse(service.scheduleDailyKtoSync(DAY).scheduled());
		assertThrows(IllegalArgumentException.class, () -> service.scheduleDailyKtoSync(null));
	}
}
