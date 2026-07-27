package koready_backend.kto.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import koready_backend.kto.domain.KtoEnglishSourceQuality;
import koready_backend.kto.domain.KtoEnglishSourceQualityWarning;

public interface KtoEnglishQualityRepository {

	List<QualityTarget> findUnclassified(long startAfterSourceRecordId, int limit);

	void classifyAll(List<QualityUpdate> updates);

	QualityCoverage summarizeLatest();

	record QualityTarget(
		long sourceRecordId,
		String sourceContentId,
		String sourceHash,
		String storageKey
	) {
	}

	record QualityUpdate(
		long sourceRecordId,
		String expectedSourceHash,
		KtoEnglishSourceQuality quality,
		Set<KtoEnglishSourceQualityWarning> warnings,
		Instant classifiedAt,
		String classifierVersion
	) {
		public QualityUpdate {
			warnings = Set.copyOf(warnings);
		}
	}

	record QualityCoverage(
		long total,
		long classified,
		long pending,
		long usable,
		long nonEnglishSuspected,
		long encodingSuspected,
		long mixedOrUnknown
	) {
	}
}
