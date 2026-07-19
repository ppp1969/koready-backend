package koready_backend.evidence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.evidence.application.exception.EvidenceBundleNotCompletedException;
import koready_backend.evidence.application.exception.InvalidEvidenceBundlePeriodException;
import koready_backend.evidence.application.port.EvidenceBundleArtifactStore;
import koready_backend.evidence.application.port.EvidenceBundleRepository;
import koready_backend.evidence.application.port.EvidenceBundleRepository.AuditRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.BundleRecord;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;

@ExtendWith(MockitoExtension.class)
class EvidenceBundleServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Mock EvidenceBundleRepository repository;
	@Mock EvidenceBundleArtifactStore artifactStore;
	EvidenceBundleService service;

	@BeforeEach
	void setUp() {
		service = new EvidenceBundleService(repository, artifactStore,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsQueuedBundleWithNormalizedFiltersAndAudit() {
		when(repository.create(any())).thenReturn(bundle(EvidenceBundleStatus.QUEUED));

		EvidenceBundleService.BundleView created = service.create(
			new EvidenceBundleService.CreateCommand(
				"  2026 공모전 OpenAPI 사용 증빙  ", NOW.minusSeconds(3600), NOW,
				List.of(ExternalApiProvider.KTO),
				List.of(" searchFestival2 ", "searchFestival2"), true, 3),
			"admin-1");

		assertEquals(EvidenceBundleStatus.QUEUED, created.status());
		ArgumentCaptor<EvidenceBundleRepository.CreateRecord> create =
			ArgumentCaptor.forClass(EvidenceBundleRepository.CreateRecord.class);
		verify(repository).create(create.capture());
		assertEquals("2026 공모전 OpenAPI 사용 증빙", create.getValue().name());
		assertEquals(List.of("searchFestival2"), create.getValue().operations());
		assertEquals("admin-1", create.getValue().createdBySubject());
		verify(repository).recordAudit(any(AuditRecord.class));
	}

	@Test
	void rejectsReversedPeriodsAndInvalidRawSampleLimits() {
		assertThrows(InvalidEvidenceBundlePeriodException.class, () -> service.create(
			new EvidenceBundleService.CreateCommand(
				"증빙", NOW, NOW.minusSeconds(1), List.of(ExternalApiProvider.KTO),
				List.of(), true, 3), "admin-1"));
		assertThrows(IllegalArgumentException.class, () -> service.create(
			new EvidenceBundleService.CreateCommand(
				"증빙", NOW.minusSeconds(1), NOW, List.of(ExternalApiProvider.KTO),
				List.of(), true, 101), "admin-1"));
	}

	@Test
	void issuesDownloadUrlOnlyForCompletedBundleAndAuditsWithoutUrl() {
		BundleRecord completed = bundle(EvidenceBundleStatus.COMPLETED);
		when(repository.findByBundleId("evidence_01J2ABCDEF"))
			.thenReturn(Optional.of(completed));
		when(artifactStore.createDownloadUrl(completed.storageKey(), completed.fileName()))
			.thenReturn(new EvidenceBundleArtifactStore.DownloadLink(
				"https://private.example/bundle?signature=temporary", NOW.plusSeconds(300)));

		EvidenceBundleService.DownloadView result = service.createDownloadUrl(
			"evidence_01J2ABCDEF", "auditor-1");

		assertEquals(completed.fileName(), result.fileName());
		assertEquals(completed.sha256(), result.sha256());
		ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
		verify(repository).recordAudit(audit.capture());
		assertEquals("EVIDENCE_BUNDLE_DOWNLOAD_URL_ISSUED", audit.getValue().action());
		assertEquals("auditor-1", audit.getValue().actorSubject());
		assertEquals(completed.sha256(), audit.getValue().details().get("sha256"));
	}

	@Test
	void rejectsDownloadBeforeCompletion() {
		when(repository.findByBundleId("evidence_01J2ABCDEF"))
			.thenReturn(Optional.of(bundle(EvidenceBundleStatus.RUNNING)));
		assertThrows(EvidenceBundleNotCompletedException.class, () ->
			service.createDownloadUrl("evidence_01J2ABCDEF", "admin-1"));
	}

	private static BundleRecord bundle(EvidenceBundleStatus status) {
		boolean completed = status == EvidenceBundleStatus.COMPLETED;
		return new BundleRecord(
			1L, "evidence_01J2ABCDEF", "2026 공모전 OpenAPI 사용 증빙", status,
			NOW.minusSeconds(3600), NOW, List.of(ExternalApiProvider.KTO),
			List.of("searchFestival2"), true, 3,
			completed ? "evidence-bundles/evidence_01J2ABCDEF.zip" : null,
			completed ? "koready-openapi-evidence-202607.zip" : null,
			completed ? "a".repeat(64) : null, completed ? 1024L : null,
			completed ? 10L : null, completed ? 2L : null,
			"admin-1", NOW.minusSeconds(60),
			status == EvidenceBundleStatus.QUEUED ? null : NOW.minusSeconds(30),
			completed ? NOW : null, null, List.of(), List.of());
	}
}
