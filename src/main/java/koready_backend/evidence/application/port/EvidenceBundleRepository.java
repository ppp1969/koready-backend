package koready_backend.evidence.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import koready_backend.evidence.domain.EvidenceBundleStatus;
import koready_backend.externalapi.domain.ExternalApiProvider;

public interface EvidenceBundleRepository {

	BundleRecord create(CreateRecord record);

	List<BundleRecord> findPage(Long beforeId, int limit);

	Optional<BundleRecord> findByBundleId(String bundleId);

	Optional<BundleRecord> claimNextQueued(Instant startedAt);

	void complete(CompletionRecord completion);

	void fail(String bundleId, String failureReason, Instant finishedAt);

	void recordAudit(AuditRecord audit);

	record CreateRecord(
		String bundleId,
		String name,
		Instant from,
		Instant to,
		List<ExternalApiProvider> providers,
		List<String> operations,
		boolean includeRawSnapshots,
		int rawSampleLimitPerOperation,
		String createdBySubject,
		Instant createdAt
	) {
	}

	record BundleRecord(
		long id,
		String bundleId,
		String name,
		EvidenceBundleStatus status,
		Instant from,
		Instant to,
		List<ExternalApiProvider> providers,
		List<String> operations,
		boolean includeRawSnapshots,
		int rawSampleLimitPerOperation,
		String storageKey,
		String fileName,
		String sha256,
		Long byteSize,
		Long callCount,
		Long rawSnapshotCount,
		String createdBySubject,
		Instant createdAt,
		Instant startedAt,
		Instant finishedAt,
		String failureReason,
		List<ExclusionRecord> exclusions,
		List<ManifestFileRecord> manifestFiles
	) {
	}

	record CompletionRecord(
		String bundleId,
		String storageKey,
		String fileName,
		String sha256,
		long byteSize,
		long callCount,
		long rawSnapshotCount,
		List<ExclusionRecord> exclusions,
		List<ManifestFileRecord> manifestFiles,
		Instant finishedAt
	) {
	}

	record ExclusionRecord(ExternalApiProvider provider, String reason) {
	}

	record ManifestFileRecord(String path, String sha256, long byteSize) {
	}

	record AuditRecord(
		String actorSubject,
		String action,
		String bundleId,
		Map<String, Object> details,
		Instant occurredAt
	) {
	}
}
