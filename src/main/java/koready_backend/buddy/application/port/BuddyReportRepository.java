package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.Optional;

import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

public interface BuddyReportRepository {

	Optional<Long> findActiveReporterUserId(String publicId);

	Optional<ProfileTarget> findActiveProfileTarget(long profileId);

	Optional<MessageTarget> findReceivedMessageTarget(
		long messageId,
		long reporterUserId
	);

	Optional<StoredReport> findByIdempotencyKey(
		long reporterUserId,
		String idempotencyKey
	);

	StoredReport save(NewReport report);

	record ProfileTarget(long profileId, long ownerUserId) {
	}

	record MessageTarget(long messageId, long senderProfileId) {
	}

	record NewReport(
		long reporterUserId,
		ReportTargetType targetType,
		long targetProfileId,
		Long targetMessageId,
		String reason,
		String idempotencyKey,
		String requestHash,
		Instant createdAt
	) {
	}

	record StoredReport(
		long reportId,
		ReportTargetType targetType,
		String targetId,
		ReportStatus status,
		Instant createdAt,
		String requestHash
	) {
	}
}
