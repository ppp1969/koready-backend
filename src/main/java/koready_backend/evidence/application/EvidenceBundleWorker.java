package koready_backend.evidence.application;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import koready_backend.evidence.application.port.EvidenceBundleArtifactStore;
import koready_backend.evidence.application.port.EvidenceBundleRepository;
import koready_backend.evidence.application.port.EvidenceBundleRepository.AuditRecord;
import koready_backend.evidence.application.port.EvidenceBundleRepository.CompletionRecord;

@Component
public class EvidenceBundleWorker {

	private static final String SAFE_FAILURE_REASON = "Evidence bundle generation failed.";

	private final EvidenceBundleRepository repository;
	private final EvidenceBundleArchiveGenerator generator;
	private final EvidenceBundleArtifactStore artifactStore;
	private final Clock clock;

	@Autowired
	public EvidenceBundleWorker(
		EvidenceBundleRepository repository,
		EvidenceBundleArchiveGenerator generator,
		EvidenceBundleArtifactStore artifactStore
	) {
		this(repository, generator, artifactStore, Clock.systemUTC());
	}

	EvidenceBundleWorker(
		EvidenceBundleRepository repository,
		EvidenceBundleArchiveGenerator generator,
		EvidenceBundleArtifactStore artifactStore,
		Clock clock
	) {
		this.repository = repository;
		this.generator = generator;
		this.artifactStore = artifactStore;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${koready.evidence-bundle.worker.poll-delay:PT30S}")
	public void processScheduled() {
		processNext();
	}

	public boolean processNext() {
		var optionalBundle = repository.claimNextQueued(clock.instant());
		if (optionalBundle.isEmpty()) {
			return false;
		}
		var bundle = optionalBundle.get();
		EvidenceBundleArchiveGenerator.GeneratedArchive archive = null;
		try {
			archive = generator.generate(bundle);
			var stored = artifactStore.store(bundle.bundleId(), archive.fileName(), archive.path(),
				archive.sha256(), archive.byteSize());
			repository.complete(new CompletionRecord(
				bundle.bundleId(), stored.storageKey(), archive.fileName(), archive.sha256(),
				archive.byteSize(), archive.callCount(), archive.rawSnapshotCount(), archive.exclusions(),
				archive.manifestFiles(), clock.instant()));
			repository.recordAudit(new AuditRecord(
				bundle.createdBySubject(), "EVIDENCE_BUNDLE_COMPLETED", bundle.bundleId(),
				java.util.Map.of("sha256", archive.sha256(), "byteSize", archive.byteSize(),
					"callCount", archive.callCount(), "rawSnapshotCount", archive.rawSnapshotCount()),
				clock.instant()));
		} catch (RuntimeException exception) {
			repository.fail(bundle.bundleId(), SAFE_FAILURE_REASON, clock.instant());
			repository.recordAudit(new AuditRecord(
				bundle.createdBySubject(), "EVIDENCE_BUNDLE_FAILED", bundle.bundleId(),
				java.util.Map.of("reason", SAFE_FAILURE_REASON), clock.instant()));
		} finally {
			if (archive != null) {
				try {
					Files.deleteIfExists(archive.path());
				} catch (IOException ignored) {
					// The system temporary-file cleaner is the final fallback.
				}
			}
		}
		return true;
	}
}
