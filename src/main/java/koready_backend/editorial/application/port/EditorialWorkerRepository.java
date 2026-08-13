package koready_backend.editorial.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import koready_backend.editorial.domain.EditorialGeneration;

public interface EditorialWorkerRepository {

	long countStartedBetween(Instant startInclusive, Instant endExclusive);

	Optional<ClaimedJob> claimNext(ClaimCommand command);

	void complete(CompleteCommand command);

	void fail(FailCommand command);

	int recoverExpiredLeases(Instant now, int maxAttempts);

	record ClaimCommand(
		Instant now,
		Instant leaseExpiresAt,
		String leaseToken,
		int maxAttempts
	) {
	}

	record ClaimedJob(
		long jobId,
		String publicId,
		long placeId,
		String sourceFingerprint,
		String promptVersion,
		String leaseToken,
		int attemptCount,
		GenerationSource source,
		Instant requestedAt
	) {
	}

	record GenerationSource(
		long placeId,
		String titleKo,
		String titleEn,
		String address,
		String overviewKo,
		List<String> travelStyles,
		List<String> facts
	) {
	}

	record CompleteCommand(
		long jobId,
		String leaseToken,
		String sourceFingerprint,
		String promptVersion,
		EditorialGeneration generation,
		Instant completedAt
	) {
	}

	record FailCommand(
		long jobId,
		String leaseToken,
		boolean retry,
		String errorCode,
		String errorMessage,
		Instant failedAt,
		Instant nextAttemptAt
	) {
	}
}
