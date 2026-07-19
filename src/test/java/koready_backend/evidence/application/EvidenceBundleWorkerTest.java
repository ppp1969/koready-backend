package koready_backend.evidence.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.evidence.application.port.EvidenceBundleArtifactStore;
import koready_backend.evidence.application.port.EvidenceBundleRepository;
import koready_backend.evidence.application.port.EvidenceBundleRepository.BundleRecord;
import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;

@ExtendWith(MockitoExtension.class)
class EvidenceBundleWorkerTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Mock EvidenceBundleRepository repository;
	@Mock EvidenceBundleArchiveGenerator generator;
	@Mock EvidenceBundleArtifactStore artifactStore;

	@Test
	void completesOneQueuedBundleAndRemovesTemporaryArchive() throws Exception {
		Path temporary = Files.createTempFile("evidence-worker-test-", ".zip");
		Files.writeString(temporary, "zip");
		BundleRecord bundle = bundle();
		when(repository.claimNextQueued(NOW)).thenReturn(Optional.of(bundle));
		when(generator.generate(bundle)).thenReturn(new EvidenceBundleArchiveGenerator.GeneratedArchive(
			temporary, "koready-openapi-evidence-202607.zip", "a".repeat(64), 3,
			2, 0, List.of(), List.of()));
		when(artifactStore.store(any(), any(), any(), any(), anyLong())).thenReturn(
			new EvidenceBundleArtifactStore.StoredArtifact("evidence-bundles/evidence_01J2ABCDEF/file.zip"));

		EvidenceBundleWorker worker = new EvidenceBundleWorker(repository, generator, artifactStore,
			Clock.fixed(NOW, ZoneOffset.UTC));

		assertTrue(worker.processNext());
		assertFalse(Files.exists(temporary));
		verify(repository).complete(any());
		verify(repository).recordAudit(any());
	}

	@Test
	void marksFailureWithSafeReasonWhenGenerationFails() {
		BundleRecord bundle = bundle();
		when(repository.claimNextQueued(NOW)).thenReturn(Optional.of(bundle));
		when(generator.generate(bundle)).thenThrow(new IllegalStateException("s3://secret-key"));

		EvidenceBundleWorker worker = new EvidenceBundleWorker(repository, generator, artifactStore,
			Clock.fixed(NOW, ZoneOffset.UTC));

		assertTrue(worker.processNext());
		verify(repository).fail(bundle.bundleId(), "Evidence bundle generation failed.", NOW);
		verify(repository).recordAudit(any());
	}

	private static BundleRecord bundle() {
		return new BundleRecord(
			1, "evidence_01J2ABCDEF", "2026 공모전 OpenAPI 사용 증빙", EvidenceBundleStatus.RUNNING,
			NOW.minusSeconds(3600), NOW, List.of(ExternalApiProvider.KTO), List.of(), true, 3,
			null, null, null, null, null, null, "admin-1", NOW.minusSeconds(60), NOW,
			null, null, List.of(), List.of());
	}
}
