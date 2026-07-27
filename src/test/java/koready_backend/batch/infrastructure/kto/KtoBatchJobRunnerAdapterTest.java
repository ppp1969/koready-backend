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
import koready_backend.kto.application.KtoDetailEnrichmentService;
import koready_backend.kto.application.KtoEnglishSyncImportService;
import koready_backend.kto.application.KtoEnglishQualityBackfillService;
import koready_backend.kto.application.KtoFestivalImportService;
import koready_backend.kto.application.KtoPhotoAwardImportService;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDailySyncResult;
import koready_backend.kto.application.model.KtoDetailEnrichmentResult;
import koready_backend.kto.application.model.KtoEnglishSyncResult;
import koready_backend.kto.application.model.KtoEnglishQualityBackfillResult;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoFestivalImportResult;
import koready_backend.kto.application.model.KtoPhotoAwardImportResult;

@ExtendWith(MockitoExtension.class)
class KtoBatchJobRunnerAdapterTest {

	@Mock
	KtoDailySyncImportService dailySyncService;

	@Mock
	KtoEnglishSyncImportService englishSyncService;

	@Mock
	KtoEnglishQualityBackfillService englishQualityBackfillService;

	@Mock
	KtoDetailEnrichmentService detailEnrichmentService;

	@Mock
	KtoFestivalImportService festivalImportService;

	@Mock
	KtoPhotoAwardImportService photoAwardImportService;

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
	void forwardsThePhotoAwardJobAndSchedulesTheNextBoundedPage() {
		when(photoAwardImportService.importAwards(any(), any()))
			.thenReturn(new KtoPhotoAwardImportResult(
				1, 1, 200, 0, 250, 1, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_PHOTO_AWARD_SYNC,
			Map.of("startPage", 1, "maxPages", 1),
			47L));

		verify(photoAwardImportService).importAwards(any(), any());
		assertEquals(200, result.successCount());
		assertEquals(
			BatchJobType.KTO_PHOTO_AWARD_SYNC,
			result.continuation().jobType());
		assertEquals(2, result.continuation().parameters().get("startPage"));
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

	@Test
	void schedulesTheNextBoundedRangeWhenTheEnglishCatalogHasMorePages() {
		when(englishSyncService.sync(any(), any())).thenReturn(new KtoEnglishSyncResult(
			20, 4_000, 3_100, 120, 780, 3_100, 0, 25_348, 40, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_EN_SYNC,
			Map.of("startPage", 21, "maxPages", 20),
			47L));

		assertEquals(BatchJobType.KTO_EN_SYNC, result.continuation().jobType());
		assertEquals(41, result.continuation().parameters().get("startPage"));
		assertEquals(20, result.continuation().parameters().get("maxPages"));
		assertEquals(4_000, result.successCount());
	}

	@Test
	void endsTheEnglishCatalogJobAtTheLastPage() {
		when(englishSyncService.sync(any(), any())).thenReturn(new KtoEnglishSyncResult(
			7, 1_348, 1_000, 48, 300, 1_000, 0, 25_348, 127, false));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_EN_SYNC,
			Map.of("startPage", 121, "maxPages", 20),
			47L));

		assertNull(result.continuation());
	}

	@Test
	void schedulesTheNextDetailPlaceRangeOnlyWhenExplicitlyEnabled() {
		when(detailEnrichmentService.enrich(any(), any()))
			.thenReturn(new KtoDetailEnrichmentResult(10, 40, 140L, true, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			Map.of(
				"startAfterPlaceId", 120L,
				"maxPlaces", 10,
				"autoContinue", true),
			47L));

		assertEquals(10, result.successCount());
		assertEquals(
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			result.continuation().jobType());
		assertEquals(
			140L,
			result.continuation().parameters().get("startAfterPlaceId"));
	}

	@Test
	void stopsTheDetailRangeWhenAutomaticContinuationIsDisabled() {
		when(detailEnrichmentService.enrich(any(), any()))
			.thenReturn(new KtoDetailEnrichmentResult(10, 40, 140L, true, false));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			Map.of(
				"startAfterPlaceId", 120L,
				"maxPlaces", 10,
				"autoContinue", false),
			47L));

		assertNull(result.continuation());
	}

	@Test
	void continuesTheEnglishQualityBackfillFromTheLastClassifiedRecord() {
		when(englishQualityBackfillService.backfill(any()))
			.thenReturn(new KtoEnglishQualityBackfillResult(
				100, 220L, true, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_EN_QUALITY_BACKFILL,
			Map.of(
				"startAfterSourceRecordId", 120L,
				"maxRecords", 100,
				"autoContinue", true),
			47L));

		assertEquals(100, result.successCount());
		assertEquals(
			BatchJobType.KTO_EN_QUALITY_BACKFILL,
			result.continuation().jobType());
		assertEquals(
			220L,
			result.continuation().parameters().get("startAfterSourceRecordId"));
	}

	private KtoBatchJobRunnerAdapter adapter() {
		return new KtoBatchJobRunnerAdapter(
			dailySyncService,
			detailEnrichmentService,
			englishSyncService,
			englishQualityBackfillService,
			festivalImportService,
			photoAwardImportService);
	}
}
