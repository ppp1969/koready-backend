package koready_backend.externalapi.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.externalapi.application.exception.ExternalApiCallNotFoundException;
import koready_backend.externalapi.application.exception.InvalidExternalApiCursorException;
import koready_backend.externalapi.application.exception.InvalidExternalApiPeriodException;
import koready_backend.externalapi.application.exception.RawSnapshotNotFoundException;
import koready_backend.externalapi.application.exception.SyncCursorNotFoundException;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.CallCriteria;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.CallRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SnapshotCriteria;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SnapshotRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SyncCursorRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SyncCursorAuditRecord;
import koready_backend.externalapi.domain.ExternalApiExposurePolicy;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.RawSnapshotStatus;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

@Service
public class ExternalApiAdminService {

	private static final Duration DEFAULT_SUMMARY_PERIOD = Duration.ofDays(30);
	private static final int MAX_CURSOR_LENGTH = 512;
	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_AUDIT_REASON_LENGTH = 500;
	private static final int MAX_SYNC_CURSOR_VALUE_LENGTH = 500;
	private static final int MAX_ACTOR_SUBJECT_LENGTH = 191;

	private final ExternalApiAdminRepository repository;
	private final Clock clock;

	@Autowired
	public ExternalApiAdminService(ExternalApiAdminRepository repository) {
		this(repository, Clock.systemUTC());
	}

	ExternalApiAdminService(ExternalApiAdminRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public OpenApiSummary summary(
		Instant from,
		Instant to,
		ExternalApiProvider provider
	) {
		Instant effectiveTo = to == null ? clock.instant() : to;
		Instant effectiveFrom = from == null
			? effectiveTo.minus(DEFAULT_SUMMARY_PERIOD)
			: from;
		validatePeriod(effectiveFrom, effectiveTo);
		var aggregate = repository.summarize(
			new ExternalApiAdminRepository.SummaryCriteria(
				effectiveFrom, effectiveTo, provider));
		double successRate = aggregate.totalCalls() == 0
			? 0.0
			: Math.round(aggregate.successCalls() * 1000.0 / aggregate.totalCalls()) / 10.0;
		return new OpenApiSummary(
			new Period(effectiveFrom, effectiveTo),
			aggregate.totalCalls(),
			aggregate.successCalls(),
			aggregate.failureCalls(),
			successRate,
			aggregate.rawSnapshotCount(),
			aggregate.providers().stream().map(providerRow -> new ProviderSummary(
				providerRow.provider(),
				providerRow.calls(),
				providerRow.success(),
				providerRow.failure(),
				providerRow.lastSuccessAt())).toList(),
			aggregate.recentFailures().stream().map(this::callView).toList());
	}

	@Transactional(readOnly = true)
	public CallPage listCalls(CallQuery query) {
		CallQuery normalized = normalize(query);
		Long beforeId = decodeCursor(
			normalized.cursor(), "CALL", callFingerprint(normalized));
		List<CallRecord> rows = repository.findCallPage(new CallCriteria(
			normalized.provider(),
			normalized.apiName(),
			normalized.operation(),
			normalized.success(),
			normalized.httpStatus(),
			normalized.from(),
			normalized.to(),
			normalized.relatedJobId(),
			normalized.hasRawSnapshot(),
			beforeId,
			normalized.size() + 1));
		boolean hasMore = rows.size() > normalized.size();
		List<CallRecord> visible = rows.subList(0, Math.min(normalized.size(), rows.size()));
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor("CALL", callFingerprint(normalized), visible.getLast().id())
			: null;
		return new CallPage(
			visible.stream().map(this::callView).toList(), nextCursor, hasMore);
	}

	@Transactional(readOnly = true)
	public CallView getCall(long callLogId) {
		if (callLogId <= 0) {
			throw new IllegalArgumentException("Call log ID must be positive");
		}
		return repository.findCallById(callLogId)
			.map(this::callView)
			.orElseThrow(() -> new ExternalApiCallNotFoundException(callLogId));
	}

	@Transactional(readOnly = true)
	public SnapshotPage listSnapshots(SnapshotQuery query) {
		SnapshotQuery normalized = normalize(query);
		Long beforeId = decodeCursor(
			normalized.cursor(), "SNAPSHOT", snapshotFingerprint(normalized));
		List<SnapshotRecord> rows = repository.findSnapshotPage(new SnapshotCriteria(
			normalized.provider(),
			normalized.operation(),
			normalized.retentionClass(),
			normalized.from(),
			normalized.to(),
			beforeId,
			normalized.size() + 1));
		boolean hasMore = rows.size() > normalized.size();
		List<SnapshotRecord> visible = rows.subList(
			0, Math.min(normalized.size(), rows.size()));
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor(
				"SNAPSHOT", snapshotFingerprint(normalized), visible.getLast().id())
			: null;
		return new SnapshotPage(
			visible.stream().map(ExternalApiAdminService::snapshotView).toList(),
			nextCursor,
			hasMore);
	}

	@Transactional(readOnly = true)
	public SnapshotView getSnapshot(long snapshotId) {
		if (snapshotId <= 0) {
			throw new IllegalArgumentException("Snapshot ID must be positive");
		}
		return repository.findSnapshotById(snapshotId)
			.map(ExternalApiAdminService::snapshotView)
			.orElseThrow(() -> new RawSnapshotNotFoundException(snapshotId));
	}

	@Transactional(readOnly = true)
	public List<SyncCursorView> listSyncCursors() {
		return repository.findSyncCursors().stream()
			.map(ExternalApiAdminService::syncCursorView)
			.toList();
	}

	@Transactional
	public SyncCursorView updateSyncCursorEnabled(
		long cursorId,
		boolean enabled,
		String reason,
		String actorSubject
	) {
		validateSyncCursorId(cursorId);
		String normalizedReason = requireText(
			reason, MAX_AUDIT_REASON_LENGTH, "Audit reason");
		String normalizedActor = requireText(
			actorSubject, MAX_ACTOR_SUBJECT_LENGTH, "Actor subject");
		SyncCursorRecord before = lockedSyncCursor(cursorId);
		Instant now = clock.instant();
		SyncCursorRecord after = repository.updateSyncCursorEnabled(
			cursorId, enabled, now);
		repository.recordSyncCursorAudit(new SyncCursorAuditRecord(
			normalizedActor,
			"SYNC_CURSOR_ENABLED_UPDATED",
			cursorId,
			normalizedReason,
			Collections.singletonMap("enabled", before.enabled()),
			Collections.singletonMap("enabled", after.enabled()),
			now));
		return syncCursorView(after);
	}

	@Transactional
	public SyncCursorView resetSyncCursor(
		long cursorId,
		String cursorValue,
		String reason,
		String actorSubject
	) {
		validateSyncCursorId(cursorId);
		String normalizedCursorValue = requireText(
			cursorValue, MAX_SYNC_CURSOR_VALUE_LENGTH, "Cursor value");
		String normalizedReason = requireText(
			reason, MAX_AUDIT_REASON_LENGTH, "Audit reason");
		String normalizedActor = requireText(
			actorSubject, MAX_ACTOR_SUBJECT_LENGTH, "Actor subject");
		SyncCursorRecord before = lockedSyncCursor(cursorId);
		Instant now = clock.instant();
		SyncCursorRecord after = repository.resetSyncCursor(
			cursorId, normalizedCursorValue, now);
		repository.recordSyncCursorAudit(new SyncCursorAuditRecord(
			normalizedActor,
			"SYNC_CURSOR_RESET",
			cursorId,
			normalizedReason,
			Collections.singletonMap("cursorValue", before.cursorValue()),
			Collections.singletonMap("cursorValue", after.cursorValue()),
			now));
		return syncCursorView(after);
	}

	private SyncCursorRecord lockedSyncCursor(long cursorId) {
		return repository.findSyncCursorByIdForUpdate(cursorId)
			.orElseThrow(() -> new SyncCursorNotFoundException(cursorId));
	}

	private static void validateSyncCursorId(long cursorId) {
		if (cursorId <= 0) {
			throw new IllegalArgumentException("Sync cursor ID must be positive");
		}
	}

	private static String requireText(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(field + " is too long");
		}
		return normalized;
	}

	private CallView callView(CallRecord row) {
		RawSnapshotStatus snapshotStatus = snapshotStatus(row);
		SnapshotRecord snapshot = row.snapshot();
		RelatedSnapshot relatedSnapshot = snapshot == null ? null : new RelatedSnapshot(
			snapshot.id(),
			snapshotStatus,
			snapshot.rawContentSha256(),
			snapshot.storedObjectSha256(),
			snapshot.byteSize(),
			false);
		return new CallView(
			row.id(),
			row.provider(),
			row.apiName(),
			row.operation(),
			row.requestStartedAt(),
			row.responseReceivedAt(),
			zeroIfNull(row.durationMs()),
			row.success(),
			row.httpStatus(),
			row.externalResultCode(),
			row.itemCount(),
			zeroIfNull(row.responseBytes()),
			snapshotStatus,
			row.relatedJobId(),
			ExternalApiExposurePolicy.safeEndpoint(row.endpoint()),
			ExternalApiExposurePolicy.safeRequestParams(row.requestParams()),
			responseSummary(row.responseSummary()),
			row.success() ? null : new CallError(
				"EXTERNAL_API_CALL_FAILED", "External API call failed."),
			row.relatedJobId() == null ? null : new RelatedJob(
				row.relatedJobId(), row.relatedJobType()),
			relatedSnapshot);
	}

	private RawSnapshotStatus snapshotStatus(CallRecord row) {
		if (row.snapshot() == null) {
			return row.provider() == ExternalApiProvider.KTO
				? RawSnapshotStatus.NOT_CAPTURED
				: RawSnapshotStatus.NOT_APPLICABLE;
		}
		Instant retentionUntil = row.snapshot().retentionUntil();
		return retentionUntil != null && !clock.instant().isBefore(retentionUntil)
			? RawSnapshotStatus.EXPIRED
			: RawSnapshotStatus.AVAILABLE;
	}

	private static ResponseSummary responseSummary(Map<String, Object> values) {
		if (values == null || values.isEmpty()) {
			return new ResponseSummary(null, null, null, null);
		}
		return new ResponseSummary(
			stringValue(values.get("resultCode")),
			stringValue(values.get("resultMessage")),
			integerValue(values.get("totalCount")),
			integerValue(values.get("itemCount")));
	}

	private static SnapshotView snapshotView(SnapshotRecord row) {
		return new SnapshotView(
			row.id(),
			row.callLogId(),
			row.provider(),
			row.apiName(),
			row.operation(),
			row.storageKey(),
			row.storageFormat(),
			row.contentType(),
			row.rawContentSha256(),
			row.storedObjectSha256(),
			row.byteSize(),
			row.compressedByteSize(),
			row.itemCount(),
			row.capturedAt(),
			row.retentionClass(),
			row.retentionUntil(),
			row.immutable(),
			false);
	}

	private static SyncCursorView syncCursorView(SyncCursorRecord row) {
		return new SyncCursorView(
			row.id(),
			row.provider(),
			row.apiName(),
			row.operation(),
			row.cursorType(),
			row.cursorValue(),
			row.lastSuccessAt(),
			row.lastFailureAt(),
			row.failureCount(),
			row.enabled(),
			row.createdAt(),
			row.updatedAt());
	}

	private static CallQuery normalize(CallQuery query) {
		if (query == null || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Invalid call log page parameters");
		}
		validatePeriodIfPresent(query.from(), query.to());
		if (query.relatedJobId() != null && query.relatedJobId() <= 0) {
			throw new IllegalArgumentException("Related job ID must be positive");
		}
		if (query.httpStatus() != null
			&& (query.httpStatus() < 100 || query.httpStatus() > 599)) {
			throw new IllegalArgumentException("HTTP status is outside the valid range");
		}
		return new CallQuery(
			query.provider(),
			normalizeText(query.apiName(), 100),
			normalizeText(query.operation(), 100),
			query.success(),
			query.httpStatus(),
			query.from(),
			query.to(),
			query.relatedJobId(),
			query.hasRawSnapshot(),
			query.cursor(),
			query.size());
	}

	private static SnapshotQuery normalize(SnapshotQuery query) {
		if (query == null || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Invalid snapshot page parameters");
		}
		validatePeriodIfPresent(query.from(), query.to());
		return new SnapshotQuery(
			query.provider(),
			normalizeText(query.operation(), 100),
			query.retentionClass(),
			query.from(),
			query.to(),
			query.cursor(),
			query.size());
	}

	private static String normalizeText(String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException("Filter value is too long");
		}
		return normalized;
	}

	private static void validatePeriodIfPresent(Instant from, Instant to) {
		if (from != null && to != null) {
			validatePeriod(from, to);
		}
	}

	private static void validatePeriod(Instant from, Instant to) {
		if (from == null || to == null || !from.isBefore(to)) {
			throw new InvalidExternalApiPeriodException();
		}
	}

	private static String callFingerprint(CallQuery query) {
		return fingerprint(
			name(query.provider()),
			query.apiName(),
			query.operation(),
			stringValue(query.success()),
			stringValue(query.httpStatus()),
			stringValue(query.from()),
			stringValue(query.to()),
			stringValue(query.relatedJobId()),
			stringValue(query.hasRawSnapshot()),
			String.valueOf(query.size()));
	}

	private static String snapshotFingerprint(SnapshotQuery query) {
		return fingerprint(
			name(query.provider()),
			query.operation(),
			name(query.retentionClass()),
			stringValue(query.from()),
			stringValue(query.to()),
			String.valueOf(query.size()));
	}

	private static String encodeCursor(String kind, String fingerprint, long beforeId) {
		String value = "1\t" + kind + "\t" + fingerprint + "\t" + beforeId;
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static Long decodeCursor(String token, String kind, String fingerprint) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidExternalApiCursorException();
		}
		try {
			String value = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = value.split("\t", -1);
			if (parts.length != 4
				|| !"1".equals(parts[0])
				|| !kind.equals(parts[1])
				|| !fingerprint.equals(parts[2])) {
				throw new InvalidExternalApiCursorException();
			}
			long id = Long.parseLong(parts[3]);
			if (id <= 0) {
				throw new InvalidExternalApiCursorException();
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new InvalidExternalApiCursorException();
		}
	}

	private static String fingerprint(String... values) {
		String value = String.join("\n", java.util.Arrays.stream(values)
			.map(item -> item == null ? "" : item)
			.toList());
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static Integer integerValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.valueOf(String.valueOf(value));
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static long zeroIfNull(Long value) {
		return value == null ? 0L : value;
	}

	public record Period(Instant from, Instant to) {
	}

	public record OpenApiSummary(
		Period period,
		long totalCalls,
		long successCalls,
		long failureCalls,
		double successRate,
		long rawSnapshotCount,
		List<ProviderSummary> providers,
		List<CallView> recentFailures
	) {
	}

	public record ProviderSummary(
		ExternalApiProvider provider,
		long calls,
		long success,
		long failure,
		Instant lastSuccessAt
	) {
	}

	public record CallQuery(
		ExternalApiProvider provider,
		String apiName,
		String operation,
		Boolean success,
		Integer httpStatus,
		Instant from,
		Instant to,
		Long relatedJobId,
		Boolean hasRawSnapshot,
		String cursor,
		int size
	) {
	}

	public record CallPage(List<CallView> items, String nextCursor, boolean hasMore) {
	}

	public record CallView(
		long callLogId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		Instant requestStartedAt,
		Instant responseReceivedAt,
		long durationMs,
		boolean success,
		Integer httpStatus,
		String externalResultCode,
		Integer itemCount,
		long responseBytes,
		RawSnapshotStatus rawSnapshotStatus,
		Long relatedJobId,
		String endpoint,
		Map<String, String> requestParamsMasked,
		ResponseSummary responseSummary,
		CallError error,
		RelatedJob relatedJob,
		RelatedSnapshot rawSnapshot
	) {
	}

	public record ResponseSummary(
		String resultCode,
		String resultMessage,
		Integer totalCount,
		Integer itemCount
	) {
	}

	public record CallError(String code, String message) {
	}

	public record RelatedJob(long jobId, String jobType) {
	}

	public record RelatedSnapshot(
		long snapshotId,
		RawSnapshotStatus status,
		String rawContentSha256,
		String storedObjectSha256,
		long byteSize,
		boolean downloadable
	) {
	}

	public record SnapshotQuery(
		ExternalApiProvider provider,
		String operation,
		SnapshotRetentionClass retentionClass,
		Instant from,
		Instant to,
		String cursor,
		int size
	) {
	}

	public record SnapshotPage(
		List<SnapshotView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record SnapshotView(
		long snapshotId,
		long callLogId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		String storageKey,
		SnapshotStorageFormat storageFormat,
		String contentType,
		String rawContentSha256,
		String storedObjectSha256,
		long byteSize,
		long compressedByteSize,
		int itemCount,
		Instant capturedAt,
		SnapshotRetentionClass retentionClass,
		Instant retentionUntil,
		boolean immutable,
		boolean downloadable
	) {
	}

	public record SyncCursorView(
		long cursorId,
		ExternalApiProvider provider,
		String apiName,
		String operation,
		SyncCursorType cursorType,
		String cursorValue,
		Instant lastSuccessAt,
		Instant lastFailureAt,
		int failureCount,
		boolean enabled,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
