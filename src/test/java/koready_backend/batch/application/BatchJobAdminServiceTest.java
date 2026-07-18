package koready_backend.batch.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.batch.application.exception.BatchJobNotFoundException;
import koready_backend.batch.application.exception.InvalidBatchJobCursorException;
import koready_backend.batch.application.port.BatchJobAdminRepository;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchItemCriteria;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchItemRecord;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchJobCriteria;
import koready_backend.batch.application.port.BatchJobAdminRepository.BatchJobRecord;
import koready_backend.batch.domain.BatchItemStatus;
import koready_backend.batch.domain.BatchItemTargetType;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@ExtendWith(MockitoExtension.class)
class BatchJobAdminServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T11:00:00Z");

	@Mock
	BatchJobAdminRepository repository;

	@Test
	void bindsJobCursorToEveryFilterAndSanitizesDetails() {
		BatchJobAdminService service = new BatchJobAdminService(repository);
		when(repository.findJobPage(any())).thenReturn(List.of(job(9L), job(8L)));
		BatchJobAdminService.JobQuery query = new BatchJobAdminService.JobQuery(
			BatchJobType.KTO_DAILY_SYNC,
			BatchJobStatus.FAILED,
			BatchTriggerSource.SCHEDULED,
			NOW.minusSeconds(3600),
			NOW,
			null,
			1);

		BatchJobAdminService.JobPage first = service.listJobs(query);
		service.listJobs(new BatchJobAdminService.JobQuery(
			query.jobType(),
			query.status(),
			query.triggerSource(),
			query.from(),
			query.to(),
			first.nextCursor(),
			query.size()));

		ArgumentCaptor<BatchJobCriteria> captor =
			ArgumentCaptor.forClass(BatchJobCriteria.class);
		verify(repository, times(2)).findJobPage(captor.capture());
		assertEquals(9L, captor.getAllValues().get(1).beforeId());
		assertTrue(first.hasMore());
		assertEquals("***", first.items().getFirst().parameters().get("serviceKey"));
		assertFalse(first.items().getFirst().parameters().containsKey("query"));
		assertEquals("Batch job failed.", first.items().getFirst().message());
		assertThrows(InvalidBatchJobCursorException.class, () -> service.listJobs(
			new BatchJobAdminService.JobQuery(
				query.jobType(),
				BatchJobStatus.COMPLETED,
				query.triggerSource(),
				query.from(),
				query.to(),
				first.nextCursor(),
				query.size())));
	}

	@Test
	void loadsJobDetailsAndReportsMissingJobs() {
		BatchJobAdminService service = new BatchJobAdminService(repository);
		when(repository.findJobById(7L)).thenReturn(Optional.of(job(7L)));
		when(repository.findJobById(404L)).thenReturn(Optional.empty());

		assertEquals(3L, service.getJob(7L).parentJobId());
		assertThrows(BatchJobNotFoundException.class, () -> service.getJob(404L));
	}

	@Test
	void listsItemsOnlyForAnExistingJobAndBindsTheCursor() {
		BatchJobAdminService service = new BatchJobAdminService(repository);
		when(repository.findJobById(7L)).thenReturn(Optional.of(job(7L)));
		when(repository.findItemPage(any())).thenReturn(List.of(item(5L), item(4L)));
		BatchJobAdminService.ItemQuery query = new BatchJobAdminService.ItemQuery(
			BatchItemStatus.FAILED,
			BatchItemTargetType.API_PAGE,
			null,
			1);

		BatchJobAdminService.ItemPage first = service.listItems(7L, query);
		service.listItems(7L, new BatchJobAdminService.ItemQuery(
			query.status(), query.targetType(), first.nextCursor(), query.size()));

		ArgumentCaptor<BatchItemCriteria> captor =
			ArgumentCaptor.forClass(BatchItemCriteria.class);
		verify(repository, times(2)).findItemPage(captor.capture());
		assertEquals(5L, captor.getAllValues().get(1).beforeId());
		assertEquals("Batch item failed.", first.items().getFirst().errorMessage());
		assertTrue(first.hasMore());

		when(repository.findJobById(8L)).thenReturn(Optional.empty());
		assertThrows(BatchJobNotFoundException.class, () -> service.listItems(8L, query));
	}

	private static BatchJobRecord job(long id) {
		return new BatchJobRecord(
			id,
			BatchJobType.KTO_DAILY_SYNC,
			BatchJobStatus.FAILED,
			NOW.minusSeconds(20),
			NOW.minusSeconds(10),
			2,
			1,
			1,
			"provider secret error",
			BatchTriggerSource.SCHEDULED,
			42L,
			3L,
			Map.of("serviceKey", "secret", "query", "private", "pageSize", 100),
			NOW.minusSeconds(30),
			NOW.minusSeconds(10));
	}

	private static BatchItemRecord item(long id) {
		return new BatchItemRecord(
			id,
			7L,
			BatchItemTargetType.API_PAGE,
			"searchFestival2:1",
			BatchItemStatus.FAILED,
			"secret provider failure",
			NOW.minusSeconds(15),
			NOW.minusSeconds(10));
	}
}
