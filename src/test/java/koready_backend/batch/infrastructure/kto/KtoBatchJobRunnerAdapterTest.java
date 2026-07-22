package koready_backend.batch.infrastructure.kto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.batch.application.port.BatchJobExecutionRepository.ClaimedJob;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.kto.application.KtoDailySyncImportService;
import koready_backend.kto.application.KtoFestivalImportService;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDailySyncResult;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoFestivalImportResult;

@ExtendWith(MockitoExtension.class)
class KtoBatchJobRunnerAdapterTest {

	@Mock
	KtoDailySyncImportService dailySyncService;

	@Mock
	KtoFestivalImportService festivalImportService;

	@Test
	void forwardsTheClaimedDailyJobAndItemIdsToTheKtoImport() {
		when(dailySyncService.sync(any(), any())).thenReturn(new KtoDailySyncResult(1, 200, 0, 200, 1, false));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_DAILY_SYNC,
			Map.of("startPage", 1, "maxPages", 1),
			47L));

		ArgumentCaptor<KtoBatchExecutionReference> execution = ArgumentCaptor.forClass(KtoBatchExecutionReference.class);
		verify(dailySyncService).sync(any(), execution.capture());
		assertEquals(new KtoBatchExecutionReference(31L, 47L), execution.getValue());
		assertEquals(200, result.successCount());
	}

	@Test
	void forwardsTheClaimedFestivalJobAndItemIdsToTheKtoImport() {
		when(festivalImportService.importFestivals(any(), any())).thenReturn(new KtoFestivalImportResult(
			LocalDate.of(2026, 7, 1), 1, 1, 200, 0, 200, 1, false));

		adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of("startPage", 1, "maxPages", 1, "eventStartDate", "2026-07-01"),
			47L));

		ArgumentCaptor<KtoFestivalImportRequest> request = ArgumentCaptor.forClass(KtoFestivalImportRequest.class);
		ArgumentCaptor<KtoBatchExecutionReference> execution = ArgumentCaptor.forClass(KtoBatchExecutionReference.class);
		verify(festivalImportService).importFestivals(request.capture(), execution.capture());
		assertEquals(LocalDate.of(2026, 7, 1), request.getValue().eventStartDate());
		assertEquals(new KtoBatchExecutionReference(31L, 47L), execution.getValue());
	}

	@Test
	void schedulesTheNextBoundedRangeWhenTheFullCatalogJobHasMorePages() {
		when(dailySyncService.sync(any(), any())).thenReturn(new KtoDailySyncResult(
			20, 4_000, 0, 68_524, 20, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_FULL_CATALOG_SYNC,
			Map.of("startPage", 1, "maxPages", 20),
			47L));

		assertEquals(BatchJobType.KTO_FULL_CATALOG_SYNC, result.continuation().jobType());
		assertEquals(21, result.continuation().parameters().get("startPage"));
		assertEquals(20, result.continuation().parameters().get("maxPages"));
	}

	@Test
	void endsTheFullCatalogJobWithoutAContinuationAtTheLastPage() {
		when(dailySyncService.sync(any(), any())).thenReturn(new KtoDailySyncResult(
			3, 524, 0, 68_524, 343, false));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_FULL_CATALOG_SYNC,
			Map.of("startPage", 341, "maxPages", 20),
			47L));

		assertNull(result.continuation());
	}

	private KtoBatchJobRunnerAdapter adapter() {
		return new KtoBatchJobRunnerAdapter(dailySyncService, festivalImportService);
	}
}
