package koready_backend.buddy.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.ReportIdempotencyConflictException;
import koready_backend.buddy.application.exception.ReportNotAllowedException;
import koready_backend.buddy.application.exception.ReportTargetNotFoundException;
import koready_backend.buddy.application.port.BuddyReportRepository;
import koready_backend.buddy.application.port.BuddyReportRepository.MessageTarget;
import koready_backend.buddy.application.port.BuddyReportRepository.NewReport;
import koready_backend.buddy.application.port.BuddyReportRepository.ProfileTarget;
import koready_backend.buddy.application.port.BuddyReportRepository.StoredReport;
import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

@Service
public class BuddyReportService {

	private static final int MAX_REASON_CODE_POINTS = 500;
	private static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final BuddyReportRepository repository;
	private final Clock clock;

	@Autowired
	public BuddyReportService(BuddyReportRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	BuddyReportService(BuddyReportRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public ReportResult create(
		String userPublicId,
		String idempotencyKey,
		CreateReportCommand command
	) {
		if (command == null || command.targetType() == null) {
			throw new IllegalArgumentException("Report target type is required.");
		}
		long numericTargetId = normalizeTargetId(command.targetId());
		String targetId = Long.toString(numericTargetId);
		String reason = normalizeReason(command.reason());
		String key = normalizeIdempotencyKey(idempotencyKey);
		long reporterUserId = repository.findActiveReporterUserId(userPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		String requestHash = requestHash(
			command.targetType().name(), targetId, reason);

		StoredReport existing = repository.findByIdempotencyKey(reporterUserId, key)
			.orElse(null);
		if (existing != null) {
			return replay(existing, requestHash);
		}

		ResolvedTarget target = switch (command.targetType()) {
			case PROFILE -> resolveProfileTarget(reporterUserId, numericTargetId);
			case MESSAGE -> resolveMessageTarget(reporterUserId, numericTargetId);
		};
		StoredReport stored = repository.save(new NewReport(
			reporterUserId,
			command.targetType(),
			target.profileId(),
			target.messageId(),
			reason,
			key,
			requestHash,
			clock.instant()));
		return replay(stored, requestHash);
	}

	private ResolvedTarget resolveProfileTarget(
		long reporterUserId,
		long profileId
	) {
		ProfileTarget target = repository.findActiveProfileTarget(profileId)
			.orElseThrow(ReportTargetNotFoundException::new);
		if (target.ownerUserId() == reporterUserId) {
			throw new ReportNotAllowedException();
		}
		return new ResolvedTarget(target.profileId(), null);
	}

	private ResolvedTarget resolveMessageTarget(
		long reporterUserId,
		long messageId
	) {
		MessageTarget target = repository.findReceivedMessageTarget(
			messageId, reporterUserId)
			.orElseThrow(ReportTargetNotFoundException::new);
		return new ResolvedTarget(target.senderProfileId(), target.messageId());
	}

	private static ReportResult replay(StoredReport report, String requestHash) {
		if (!report.requestHash().equals(requestHash)) {
			throw new ReportIdempotencyConflictException();
		}
		return new ReportResult(
			report.reportId(),
			report.targetType(),
			report.targetId(),
			report.status(),
			report.createdAt());
	}

	private static long normalizeTargetId(String targetId) {
		if (targetId == null || !targetId.matches("[1-9][0-9]*")) {
			throw new IllegalArgumentException(
				"Report target ID must be a positive decimal integer.");
		}
		try {
			return Long.parseLong(targetId);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
				"Report target ID is outside the supported range.", exception);
		}
	}

	private static String normalizeReason(String reason) {
		if (reason == null) {
			throw new IllegalArgumentException("Report reason is required.");
		}
		String normalized = reason.strip();
		int length = normalized.codePointCount(0, normalized.length());
		if (length < 1 || length > MAX_REASON_CODE_POINTS) {
			throw new IllegalArgumentException(
				"Report reason must contain between 1 and 500 characters.");
		}
		return normalized;
	}

	private static String normalizeIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null
			|| idempotencyKey.length() < MIN_IDEMPOTENCY_KEY_LENGTH
			|| idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
			|| idempotencyKey.chars().anyMatch(value -> value < 0x21 || value > 0x7e)) {
			throw new IllegalArgumentException(
				"Idempotency-Key must be 8 to 100 visible ASCII characters.");
		}
		return idempotencyKey;
	}

	private static String requestHash(String... components) {
		StringBuilder canonical = new StringBuilder();
		for (String component : components) {
			byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
			canonical.append(bytes.length).append(':').append(component).append('|');
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record CreateReportCommand(
		ReportTargetType targetType,
		String targetId,
		String reason
	) {
	}

	public record ReportResult(
		long reportId,
		ReportTargetType targetType,
		String targetId,
		ReportStatus status,
		Instant createdAt
	) {
	}

	private record ResolvedTarget(long profileId, Long messageId) {
	}
}
