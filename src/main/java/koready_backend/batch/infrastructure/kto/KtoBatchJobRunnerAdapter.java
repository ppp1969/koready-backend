package koready_backend.batch.infrastructure.kto;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Component;

import koready_backend.batch.application.port.BatchJobExecutionRepository.ClaimedJob;
import koready_backend.batch.application.port.KtoBatchJobRunner;
import koready_backend.batch.application.model.BatchJobContinuation;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.kto.application.KtoDailySyncImportService;
import koready_backend.kto.application.KtoDetailEnrichmentService;
import koready_backend.kto.application.KtoEnglishSyncImportService;
import koready_backend.kto.application.KtoEnglishQualityBackfillService;
import koready_backend.kto.application.KtoFestivalImportService;
import koready_backend.kto.application.KtoPhotoAwardImportService;
import koready_backend.kto.application.KtoPhotoGalleryImportService;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoDailySyncRequest;
import koready_backend.kto.application.model.KtoDetailEnrichmentRequest;
import koready_backend.kto.application.model.KtoEnglishSyncRequest;
import koready_backend.kto.application.model.KtoEnglishQualityBackfillRequest;
import koready_backend.kto.application.model.KtoFestivalImportRequest;
import koready_backend.kto.application.model.KtoPhotoAwardImportRequest;
import koready_backend.kto.application.model.KtoPhotoGalleryImportRequest;

@Component
public class KtoBatchJobRunnerAdapter implements KtoBatchJobRunner {

	private final KtoDailySyncImportService dailySyncService;
	private final KtoDetailEnrichmentService detailEnrichmentService;
	private final KtoEnglishSyncImportService englishSyncService;
	private final KtoEnglishQualityBackfillService englishQualityBackfillService;
	private final KtoFestivalImportService festivalImportService;
	private final KtoPhotoAwardImportService photoAwardImportService;
	private final KtoPhotoGalleryImportService photoGalleryImportService;

	public KtoBatchJobRunnerAdapter(
		KtoDailySyncImportService dailySyncService,
		KtoDetailEnrichmentService detailEnrichmentService,
		KtoEnglishSyncImportService englishSyncService,
		KtoEnglishQualityBackfillService englishQualityBackfillService,
		KtoFestivalImportService festivalImportService,
		KtoPhotoAwardImportService photoAwardImportService,
		KtoPhotoGalleryImportService photoGalleryImportService
	) {
		this.dailySyncService = dailySyncService;
		this.detailEnrichmentService = detailEnrichmentService;
		this.englishSyncService = englishSyncService;
		this.englishQualityBackfillService = englishQualityBackfillService;
		this.festivalImportService = festivalImportService;
		this.photoAwardImportService = photoAwardImportService;
		this.photoGalleryImportService = photoGalleryImportService;
	}

	@Override
	public RunResult run(ClaimedJob job) {
		var batchExecution = new KtoBatchExecutionReference(job.id(), job.itemId());
		if (job.jobType() == BatchJobType.KTO_DETAIL_ENRICHMENT) {
			long startAfterPlaceId = longInteger(
				job.parameters(), "startAfterPlaceId");
			int maxPlaces = integer(job.parameters(), "maxPlaces");
			boolean autoContinue = flag(job.parameters(), "autoContinue");
			var result = detailEnrichmentService.enrich(
				new KtoDetailEnrichmentRequest(
					startAfterPlaceId, maxPlaces, autoContinue),
				batchExecution);
			var continuation = result.hasMore() && result.autoContinue()
				? new BatchJobContinuation(
					BatchJobType.KTO_DETAIL_ENRICHMENT,
					Map.of(
						"startAfterPlaceId", result.lastProcessedPlaceId(),
						"maxPlaces", maxPlaces,
						"autoContinue", true))
				: null;
			return new RunResult(
				result.processedPlaces(),
				result.processedPlaces(),
				0,
				continuation);
		}
		if (job.jobType() == BatchJobType.KTO_EN_QUALITY_BACKFILL) {
			long startAfterSourceRecordId = longInteger(
				job.parameters(), "startAfterSourceRecordId");
			int maxRecords = integer(job.parameters(), "maxRecords");
			boolean autoContinue = flag(job.parameters(), "autoContinue");
			var result = englishQualityBackfillService.backfill(
				new KtoEnglishQualityBackfillRequest(
					startAfterSourceRecordId, maxRecords, autoContinue));
			var continuation = result.hasMore() && result.autoContinue()
				? new BatchJobContinuation(
					BatchJobType.KTO_EN_QUALITY_BACKFILL,
					Map.of(
						"startAfterSourceRecordId",
						result.lastProcessedSourceRecordId(),
						"maxRecords", maxRecords,
						"autoContinue", true))
				: null;
			return new RunResult(
				result.processedRecords(),
				result.processedRecords(),
				0,
				continuation);
		}
		int startPage = integer(job.parameters(), "startPage");
		int maxPages = integer(job.parameters(), "maxPages");
		if (job.jobType() == BatchJobType.KTO_DAILY_SYNC) {
			var result = dailySyncService.sync(new KtoDailySyncRequest(startPage, maxPages), batchExecution);
			return new RunResult(result.processedItems(), result.processedItems(), 0);
		}
		if (job.jobType() == BatchJobType.KTO_FULL_CATALOG_SYNC) {
			var result = dailySyncService.sync(new KtoDailySyncRequest(startPage, maxPages), batchExecution);
			var continuation = result.truncatedByPageLimit()
				? new BatchJobContinuation(BatchJobType.KTO_FULL_CATALOG_SYNC, Map.of(
					"startPage", result.lastProcessedPage() + 1,
					"maxPages", maxPages))
				: null;
			return new RunResult(result.processedItems(), result.processedItems(), 0, continuation);
		}
		if (job.jobType() == BatchJobType.KTO_EN_SYNC) {
			var result = englishSyncService.sync(new KtoEnglishSyncRequest(startPage, maxPages), batchExecution);
			var continuation = result.truncatedByPageLimit()
				? new BatchJobContinuation(BatchJobType.KTO_EN_SYNC, Map.of(
					"startPage", result.lastProcessedPage() + 1,
					"maxPages", maxPages))
				: null;
			return new RunResult(result.processedItems(), result.processedItems(), 0, continuation);
		}
		if (job.jobType() == BatchJobType.KTO_FESTIVAL_SYNC) {
			var result = festivalImportService.importFestivals(new KtoFestivalImportRequest(
				LocalDate.parse(string(job.parameters(), "eventStartDate")), startPage, maxPages), batchExecution);
			return new RunResult(result.processedItems(), result.processedItems(), 0);
		}
		if (job.jobType() == BatchJobType.KTO_PHOTO_AWARD_SYNC) {
			var result = photoAwardImportService.importAwards(
				new KtoPhotoAwardImportRequest(startPage, maxPages),
				batchExecution);
			var continuation = result.truncatedByPageLimit()
				? new BatchJobContinuation(
					BatchJobType.KTO_PHOTO_AWARD_SYNC,
					Map.of(
						"startPage", result.lastProcessedPage() + 1,
						"maxPages", maxPages))
				: null;
			return new RunResult(
				result.processedItems(),
				result.processedItems(),
				0,
				continuation);
		}
		if (job.jobType() == BatchJobType.KTO_PHOTO_GALLERY_SYNC) {
			var result = photoGalleryImportService.importGallery(
				new KtoPhotoGalleryImportRequest(startPage, maxPages),
				batchExecution);
			var continuation = result.truncatedByPageLimit()
				? new BatchJobContinuation(
					BatchJobType.KTO_PHOTO_GALLERY_SYNC,
					Map.of(
						"startPage",
						result.lastProcessedPage() + 1,
						"maxPages",
						maxPages))
				: null;
			return new RunResult(
				result.processedItems(),
				result.processedItems(),
				0,
				continuation);
		}
		throw new IllegalArgumentException("Unsupported manual batch job type");
	}

	private static int integer(Map<String, Object> parameters, String name) {
		Object value = parameters.get(name);
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException("Batch job parameter is invalid");
		}
		return number.intValue();
	}

	private static long longInteger(Map<String, Object> parameters, String name) {
		Object value = parameters.get(name);
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException("Batch job parameter is invalid");
		}
		return number.longValue();
	}

	private static boolean flag(Map<String, Object> parameters, String name) {
		Object value = parameters.get(name);
		if (!(value instanceof Boolean flag)) {
			throw new IllegalArgumentException("Batch job parameter is invalid");
		}
		return flag;
	}

	private static String string(Map<String, Object> parameters, String name) {
		Object value = parameters.get(name);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException("Batch job parameter is invalid");
		}
		return text;
	}
}
