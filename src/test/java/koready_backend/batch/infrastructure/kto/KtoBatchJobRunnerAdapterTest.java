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
import koready_backend.kto.application.KtoPhotoGalleryImportService;
import koready_backend.kto.application.KtoRelatedTourImportService;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDailySyncResult;
import koready_backend.kto.application.model.KtoDetailEnrichmentResult;
import koready_backend.kto.application.model.KtoEnglishSyncResult;
import koready_backend.kto.application.model.KtoEnglishQualityBackfillResult;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoFestivalImportResult;
import koready_backend.kto.application.model.KtoPhotoAwardImportResult;
import koready_backend.kto.application.model.KtoPhotoGalleryImportResult;
import koready_backend.kto.application.model.KtoRelatedTourImportResult;

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

	@Mock
	KtoPhotoGalleryImportService photoGalleryImportService;

	@Mock
	KtoRelatedTourImportService relatedTourImportService;

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
			Map.of(
				"startPage", 1,
				"maxPages", 1,
				"eventStartDate", "2026-07-01",
				"autoContinue", false),
			47L));

		ArgumentCaptor<KtoFestivalImportRequest> request = ArgumentCaptor.forClass(KtoFestivalImportRequest.class);
		ArgumentCaptor<KtoBatchExecutionReference> execution = ArgumentCaptor.forClass(KtoBatchExecutionReference.class);
		verify(festivalImportService).importFestivals(request.capture(), execution.capture());
		assertEquals(LocalDate.of(2026, 7, 1), request.getValue().eventStartDate());
		assertEquals(new KtoBatchExecutionReference(31L, 47L), execution.getValue());
	}

	@Test
	void schedulesTheNextFestivalPageWhenAutomaticContinuationIsEnabled() {
		when(festivalImportService.importFestivals(any(), any()))
			.thenReturn(new KtoFestivalImportResult(
				LocalDate.of(2026, 1, 27),
				6, 5, 1_000, 0, 2_400, 10, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of(
				"startPage", 6,
				"maxPages", 5,
				"eventStartDate", "2026-01-27",
				"autoContinue", true),
			47L));

		assertEquals(
			BatchJobType.KTO_FESTIVAL_SYNC,
			result.continuation().jobType());
		assertEquals(
			11,
			result.continuation().parameters().get("startPage"));
		assertEquals(
			"2026-01-27",
			result.continuation().parameters().get("eventStartDate"));
		assertEquals(
			true,
			result.continuation().parameters().get("autoContinue"));
	}

	@Test
	void doesNotContinueFestivalPagesWhenAutomaticContinuationIsDisabled() {
		when(festivalImportService.importFestivals(any(), any()))
			.thenReturn(new KtoFestivalImportResult(
				LocalDate.of(2026, 1, 27),
				1, 5, 1_000, 0, 2_400, 5, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of(
				"startPage", 1,
				"maxPages", 5,
				"eventStartDate", "2026-01-27",
				"autoContinue", false),
			47L));

		assertNull(result.continuation());
	}

	@Test
	void endsFestivalCollectionAtTheLastPage() {
		when(festivalImportService.importFestivals(any(), any()))
			.thenReturn(new KtoFestivalImportResult(
				LocalDate.of(2026, 1, 27),
				11, 2, 314, 0, 2_514, 12, false));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of(
				"startPage", 11,
				"maxPages", 5,
				"eventStartDate", "2026-01-27",
				"autoContinue", true),
			47L));

		assertNull(result.continuation());
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
	void forwardsThePhotoGalleryJobAndSchedulesTheNextBoundedPage() {
		when(photoGalleryImportService.importGallery(any(), any()))
			.thenReturn(new KtoPhotoGalleryImportResult(
				1, 1, 200, 0, 312, 1, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_PHOTO_GALLERY_SYNC,
			Map.of("startPage", 1, "maxPages", 1),
			47L));

		verify(photoGalleryImportService).importGallery(any(), any());
		assertEquals(200, result.successCount());
		assertEquals(
			BatchJobType.KTO_PHOTO_GALLERY_SYNC,
			result.continuation().jobType());
		assertEquals(
			2, result.continuation().parameters().get("startPage"));
	}

	@Test
	void continuesRelatedTourCollectionFromTheLastCompletedRegion() {
		when(relatedTourImportService.importRelatedTours(any(), any()))
			.thenReturn(new KtoRelatedTourImportResult(
				2, 4, 500, 0, "26:26110", true, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_RELATED_TOUR_SYNC,
			Map.of(
				"baseYearMonth", "202606",
				"startAfterRegionKey", "11:11530",
				"maxRegions", 2,
				"maxPagesPerRegion", 10,
				"autoContinue", true),
			47L));

		verify(relatedTourImportService)
			.importRelatedTours(any(), any());
		assertEquals(500, result.successCount());
		assertEquals(
			BatchJobType.KTO_RELATED_TOUR_SYNC,
			result.continuation().jobType());
		assertEquals(
			"26:26110",
			result.continuation().parameters().get(
				"startAfterRegionKey"));
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
				"remainingDailyPlaces", 800,
				"scheduleDate", "2026-07-20",
				"autoContinue", true),
			47L));

		assertEquals(10, result.successCount());
		assertEquals(
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			result.continuation().jobType());
		assertEquals(
			140L,
			result.continuation().parameters().get("startAfterPlaceId"));
		assertEquals(
			790,
			result.continuation().parameters().get("remainingDailyPlaces"));
	}

	@Test
	void endsTheDailyDetailChainWhenTheRemainingBudgetIsConsumed() {
		when(detailEnrichmentService.enrich(any(), any()))
			.thenReturn(new KtoDetailEnrichmentResult(50, 200, 170L, true, true));

		var result = adapter().run(new ClaimedJob(
			31L,
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			Map.of(
				"startAfterPlaceId", 120L,
				"maxPlaces", 50,
				"remainingDailyPlaces", 50,
				"scheduleDate", "2026-07-20",
				"autoContinue", true),
			47L));

		assertNull(result.continuation());
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
			photoAwardImportService,
			photoGalleryImportService,
			relatedTourImportService);
	}
}
