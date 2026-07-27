package koready_backend.batch.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.batch.application.exception.BatchJobRetryNotAllowedException;
import koready_backend.batch.application.port.BatchJobCommandRepository;
import koready_backend.batch.application.port.BatchJobCommandRepository.EnqueueCommand;
import koready_backend.batch.application.port.BatchJobCommandRepository.BatchAuditRecord;
import koready_backend.batch.application.port.BatchJobCommandRepository.RetrySource;
import koready_backend.batch.domain.BatchJobStatus;
import koready_backend.batch.domain.BatchJobType;
import koready_backend.batch.domain.BatchTriggerSource;

@ExtendWith(MockitoExtension.class)
class BatchJobCommandServiceTest {

	@Mock
	BatchJobCommandRepository repository;

	@Test
	void acceptsOnlyBoundedKtoParametersAndPersistsAQueuedManualJob() {
		when(repository.enqueue(any())).thenReturn(91L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of(
				"eventStartDate", "2026-07-01",
				"startPage", 2,
				"maxPages", 3,
				"autoContinue", true),
			"Refresh festival data", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(91L, accepted.jobId());
		assertEquals(BatchTriggerSource.ADMIN_MANUAL, captor.getValue().triggerSource());
		assertEquals("2026-07-01", captor.getValue().parameters().get("eventStartDate"));
		assertEquals(3, captor.getValue().parameters().get("maxPages"));
		assertEquals(true, captor.getValue().parameters().get("autoContinue"));
		ArgumentCaptor<BatchAuditRecord> auditCaptor = ArgumentCaptor.forClass(BatchAuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("operator-7", auditCaptor.getValue().actorSubject());
		assertEquals("Refresh festival data", auditCaptor.getValue().reason());
		assertThrows(IllegalArgumentException.class, () -> service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of("eventStartDate", "not-a-date"),
			"Refresh festival data", "operator-7")));
	}

	@Test
	void defaultsFestivalAutomaticContinuationToDisabled() {
		when(repository.enqueue(any())).thenReturn(92L);
		BatchJobCommandService service = service();

		service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_FESTIVAL_SYNC,
			Map.of("eventStartDate", "2026-07-01"),
			"Refresh one bounded festival range", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(false, captor.getValue().parameters().get("autoContinue"));
	}

	@Test
	void acceptsAFullCatalogJobWithABoundedPageWindow() {
		when(repository.enqueue(any())).thenReturn(93L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_FULL_CATALOG_SYNC,
			Map.of(),
			"Start the full KTO catalog import", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(BatchJobType.KTO_FULL_CATALOG_SYNC, accepted.jobType());
		assertEquals(1, captor.getValue().parameters().get("startPage"));
		assertEquals(20, captor.getValue().parameters().get("maxPages"));
	}

	@Test
	void acceptsAnEnglishCatalogJobWithTheDefaultPageWindow() {
		when(repository.enqueue(any())).thenReturn(94L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_EN_SYNC,
			Map.of(),
			"Start the English KTO catalog import", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(BatchJobType.KTO_EN_SYNC, accepted.jobType());
		assertEquals(1, captor.getValue().parameters().get("startPage"));
		assertEquals(20, captor.getValue().parameters().get("maxPages"));
	}

	@Test
	void acceptsABoundedPhotoAwardSyncJob() {
		when(repository.enqueue(any())).thenReturn(98L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_PHOTO_AWARD_SYNC,
			Map.of(),
			"Collect the KTO photo award catalog", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor =
			ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(BatchJobType.KTO_PHOTO_AWARD_SYNC, accepted.jobType());
		assertEquals(1, captor.getValue().parameters().get("startPage"));
		assertEquals(1, captor.getValue().parameters().get("maxPages"));
	}

	@Test
	void acceptsABoundedPhotoGallerySyncJob() {
		when(repository.enqueue(any())).thenReturn(99L);
		BatchJobCommandService service = service();

		var accepted = service.accept(
			new BatchJobCommandService.CreateCommand(
				BatchJobType.KTO_PHOTO_GALLERY_SYNC,
				Map.of(),
				"Collect the KTO photo gallery catalog",
				"operator-7"));

		ArgumentCaptor<EnqueueCommand> captor =
			ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(
			BatchJobType.KTO_PHOTO_GALLERY_SYNC,
			accepted.jobType());
		assertEquals(
			1, captor.getValue().parameters().get("startPage"));
		assertEquals(
			1, captor.getValue().parameters().get("maxPages"));
	}

	@Test
	void acceptsABoundedRelatedTourCoverageJob() {
		when(repository.enqueue(any())).thenReturn(100L);
		BatchJobCommandService service = service();

		var accepted = service.accept(
			new BatchJobCommandService.CreateCommand(
				BatchJobType.KTO_RELATED_TOUR_SYNC,
				Map.of(
					"baseYearMonth", "202606",
					"startAfterRegionKey", "11:11530",
					"maxRegions", 3,
					"maxPagesPerRegion", 10,
					"autoContinue", true),
				"Collect KTO related tours by region",
				"operator-7"));

		ArgumentCaptor<EnqueueCommand> captor =
			ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(
			BatchJobType.KTO_RELATED_TOUR_SYNC,
			accepted.jobType());
		assertEquals(
			"202606",
			captor.getValue().parameters().get("baseYearMonth"));
		assertEquals(
			"11:11530",
			captor.getValue().parameters().get(
				"startAfterRegionKey"));
		assertEquals(
			3, captor.getValue().parameters().get("maxRegions"));
		assertEquals(
			true,
			captor.getValue().parameters().get("autoContinue"));
	}

	@Test
	void acceptsTheProfiledJejuRelatedTourPageLimit() {
		when(repository.enqueue(any())).thenReturn(102L);
		BatchJobCommandService service = service();

		service.accept(
			new BatchJobCommandService.CreateCommand(
				BatchJobType.KTO_RELATED_TOUR_SYNC,
				Map.of(
					"baseYearMonth", "202606",
					"startAfterRegionKey", "48:48890",
					"maxRegions", 2,
					"maxPagesPerRegion", 50,
					"autoContinue", true),
				"Collect profiled Jeju related tours",
				"operator-7"));

		ArgumentCaptor<EnqueueCommand> captor =
			ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(
			50,
			captor.getValue().parameters().get(
				"maxPagesPerRegion"));
	}

	@Test
	void acceptsABoundedKtoDetailEnrichmentJob() {
		when(repository.enqueue(any())).thenReturn(95L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			Map.of("startAfterPlaceId", 120L, "maxPlaces", 10, "autoContinue", false),
			"Enrich the next bounded KTO place range", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(BatchJobType.KTO_DETAIL_ENRICHMENT, accepted.jobType());
		assertEquals(120L, captor.getValue().parameters().get("startAfterPlaceId"));
		assertEquals(10, captor.getValue().parameters().get("maxPlaces"));
		assertEquals(false, captor.getValue().parameters().get("autoContinue"));
	}

	@Test
	void defaultsKtoDetailEnrichmentToASmallManualWindow() {
		when(repository.enqueue(any())).thenReturn(96L);
		BatchJobCommandService service = service();

		service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_DETAIL_ENRICHMENT,
			Map.of(),
			"Enrich a safe sample", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(0L, captor.getValue().parameters().get("startAfterPlaceId"));
		assertEquals(10, captor.getValue().parameters().get("maxPlaces"));
		assertEquals(false, captor.getValue().parameters().get("autoContinue"));
	}

	@Test
	void acceptsABoundedEnglishQualityBackfillJob() {
		when(repository.enqueue(any())).thenReturn(97L);
		BatchJobCommandService service = service();

		var accepted = service.accept(new BatchJobCommandService.CreateCommand(
			BatchJobType.KTO_EN_QUALITY_BACKFILL,
			Map.of("startAfterSourceRecordId", 120L, "maxRecords", 100, "autoContinue", false),
			"Backfill indexed English source quality", "operator-7"));

		ArgumentCaptor<EnqueueCommand> captor = ArgumentCaptor.forClass(EnqueueCommand.class);
		verify(repository).enqueue(captor.capture());
		assertEquals(BatchJobType.KTO_EN_QUALITY_BACKFILL, accepted.jobType());
		assertEquals(120L, captor.getValue().parameters().get("startAfterSourceRecordId"));
		assertEquals(100, captor.getValue().parameters().get("maxRecords"));
		assertEquals(false, captor.getValue().parameters().get("autoContinue"));
	}

	@Test
	void retriesOnlyFailedJobsWithANewLinkedJob() {
		when(repository.findRetrySourceForUpdate(7L)).thenReturn(Optional.of(new RetrySource(
			7L, BatchJobType.KTO_DAILY_SYNC, BatchJobStatus.FAILED, Map.of("startPage", 1, "maxPages", 1))));
		when(repository.enqueue(any())).thenReturn(92L);
		BatchJobCommandService service = service();

		var accepted = service.retry(
			7L, new BatchJobCommandService.RetryCommand("FAILED_ITEMS", "Retry once", "operator-7"));

		assertEquals(7L, accepted.originalJobId());
		assertEquals(BatchTriggerSource.RETRY, accepted.triggerSource());
		ArgumentCaptor<BatchAuditRecord> auditCaptor = ArgumentCaptor.forClass(BatchAuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("BATCH_JOB_RETRY_ACCEPTED", auditCaptor.getValue().action());
		assertEquals(92L, auditCaptor.getValue().jobId());
		when(repository.findRetrySourceForUpdate(8L)).thenReturn(Optional.of(new RetrySource(
			8L, BatchJobType.KTO_DAILY_SYNC, BatchJobStatus.COMPLETED, Map.of())));
		assertThrows(BatchJobRetryNotAllowedException.class, () -> service.retry(
			8L, new BatchJobCommandService.RetryCommand("FAILED_ITEMS", "Retry once", "operator-7")));
	}

	private BatchJobCommandService service() {
		return new BatchJobCommandService(repository, Clock.fixed(
			Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
	}
}
