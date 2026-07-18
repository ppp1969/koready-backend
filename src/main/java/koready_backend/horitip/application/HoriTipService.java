package koready_backend.horitip.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.horitip.application.exception.HoriTipCodeDuplicatedException;
import koready_backend.horitip.application.exception.HoriTipConcurrentModificationException;
import koready_backend.horitip.application.exception.HoriTipNotFoundException;
import koready_backend.horitip.application.exception.InvalidHoriTipCursorException;
import koready_backend.horitip.application.port.HoriTipRepository;
import koready_backend.horitip.application.port.HoriTipRepository.AuditRecord;
import koready_backend.horitip.application.port.HoriTipRepository.HoriTipRecord;
import koready_backend.horitip.application.port.HoriTipRepository.ListCriteria;
import koready_backend.horitip.application.port.HoriTipRepository.NewHoriTip;
import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipPolicyException;
import koready_backend.horitip.domain.HoriTipScopeType;
import koready_backend.horitip.domain.HoriTipStatus;
import koready_backend.horitip.domain.HoriTipStatusTarget;

@Service
public class HoriTipService {

	private static final Pattern CODE_PATTERN = Pattern.compile("TIP_[A-Z0-9_]+");
	private static final int MAX_CURSOR_LENGTH = 256;
	private static final String SOURCE = "OPERATOR_CURATED";

	private final HoriTipRepository repository;
	private final Clock clock;

	@Autowired
	public HoriTipService(HoriTipRepository repository) {
		this(repository, Clock.systemUTC());
	}

	HoriTipService(HoriTipRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public HoriTipView create(
		CreateCommand command,
		String actorSubject,
		boolean editableAllowed
	) {
		requireEditor(editableAllowed);
		Objects.requireNonNull(command, "Create command is required");
		String code = validCode(command.code());
		HoriTipDraft draft = Objects.requireNonNull(command.draft(), "Hori Tip draft is required");
		validateDestinations(draft, HoriTipPolicyException.Reason.RULE_INVALID);
		Instant now = clock.instant();
		HoriTipRecord created = repository.insertDraft(new NewHoriTip(
			code, draft, actor(actorSubject), now))
			.orElseThrow(() -> new HoriTipCodeDuplicatedException(code));
		repository.recordAudit(new AuditRecord(
			"HORI_TIP_CREATED", actorSubject, null, null, created, now));
		return view(created, editableAllowed, now);
	}

	@Transactional(readOnly = true)
	public HoriTipView get(long horiTipId, boolean editableAllowed) {
		return view(load(horiTipId), editableAllowed, clock.instant());
	}

	@Transactional
	public HoriTipView update(
		long horiTipId,
		UpdateCommand command,
		String actorSubject,
		boolean editableAllowed
	) {
		requireEditor(editableAllowed);
		Objects.requireNonNull(command, "Update command is required");
		HoriTipRecord existing = lock(horiTipId);
		requireVersion(existing, command.version());
		requireMutable(existing);
		HoriTipDraft draft = Objects.requireNonNull(command.draft(), "Hori Tip draft is required");
		HoriTipPolicyException.Reason invalidReason = existing.status() == HoriTipStatus.ACTIVE
			? HoriTipPolicyException.Reason.ACTIVATION_INVALID
			: HoriTipPolicyException.Reason.RULE_INVALID;
		if (existing.status() == HoriTipStatus.ACTIVE) {
			draft.requireActivatable();
		}
		validateDestinations(draft, invalidReason);
		Instant now = clock.instant();
		HoriTipRecord updated = repository.updateDraft(
			horiTipId, draft, actor(actorSubject), now);
		repository.recordAudit(new AuditRecord(
			"HORI_TIP_UPDATED", actorSubject, null, existing, updated, now));
		return view(updated, editableAllowed, now);
	}

	@Transactional
	public HoriTipView changeStatus(
		long horiTipId,
		StatusCommand command,
		String actorSubject,
		boolean editableAllowed
	) {
		requireEditor(editableAllowed);
		Objects.requireNonNull(command, "Status command is required");
		HoriTipRecord existing = lock(horiTipId);
		requireVersion(existing, command.version());
		requireMutable(existing);
		HoriTipStatus target = HoriTipStatus.valueOf(
			Objects.requireNonNull(command.status(), "Target status is required").name());
		if (target == existing.status()) {
			return view(existing, editableAllowed, clock.instant());
		}
		if (target == HoriTipStatus.ACTIVE) {
			existing.draft().requireActivatable();
			validateDestinations(
				existing.draft(), HoriTipPolicyException.Reason.ACTIVATION_INVALID);
		}
		String reason = requiredReason(command.reason());
		Instant now = clock.instant();
		HoriTipRecord updated = repository.updateStatus(
			horiTipId, target, actor(actorSubject), now);
		repository.recordAudit(new AuditRecord(
			"HORI_TIP_STATUS_CHANGED", actorSubject, reason, existing, updated, now));
		return view(updated, editableAllowed, now);
	}

	@Transactional(readOnly = true)
	public HoriTipPage list(
		HoriTipStatus status,
		String code,
		Long destinationPlaceId,
		String cursor,
		int size,
		boolean editableAllowed
	) {
		if (size < 1 || size > 100 || destinationPlaceId != null && destinationPlaceId <= 0) {
			throw new IllegalArgumentException("Invalid Hori Tip list parameters");
		}
		String normalizedCode = normalizeFilter(code);
		Long beforeId = decodeCursor(cursor, status, normalizedCode, destinationPlaceId);
		List<HoriTipRecord> rows = repository.findPage(new ListCriteria(
			status, normalizedCode, destinationPlaceId, beforeId, size + 1));
		boolean hasMore = rows.size() > size;
		List<HoriTipRecord> visible = rows.subList(0, Math.min(size, rows.size()));
		Instant now = clock.instant();
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor(status, normalizedCode, destinationPlaceId, visible.getLast().id())
			: null;
		return new HoriTipPage(
			visible.stream().map(row -> view(row, editableAllowed, now)).toList(),
			nextCursor,
			hasMore);
	}

	private HoriTipRecord load(long id) {
		return repository.findById(id).orElseThrow(() -> new HoriTipNotFoundException(id));
	}

	private HoriTipRecord lock(long id) {
		return repository.findByIdForUpdate(id)
			.orElseThrow(() -> new HoriTipNotFoundException(id));
	}

	private void validateDestinations(
		HoriTipDraft draft,
		HoriTipPolicyException.Reason reason
	) {
		if (draft.scope().scopeType() == HoriTipScopeType.ALL_ROUTES) {
			return;
		}
		List<Long> expected = draft.scope().destinationPlaceIds();
		Set<Long> visible = repository.findVisiblePlaceIds(expected);
		if (visible.size() != expected.size() || !visible.containsAll(expected)) {
			throw new HoriTipPolicyException(
				reason,
				"Every Hori Tip destination must be an active, visible place");
		}
	}

	private static void requireEditor(boolean editableAllowed) {
		if (!editableAllowed) {
			throw notEditable("The current actor cannot edit Hori Tips");
		}
	}

	private static void requireMutable(HoriTipRecord existing) {
		if (existing.status() == HoriTipStatus.ARCHIVED) {
			throw notEditable("Archived Hori Tips cannot be changed");
		}
	}

	private static void requireVersion(HoriTipRecord existing, int version) {
		if (version < 1 || existing.version() != version) {
			throw new HoriTipConcurrentModificationException();
		}
	}

	private static HoriTipPolicyException notEditable(String message) {
		return new HoriTipPolicyException(HoriTipPolicyException.Reason.NOT_EDITABLE, message);
	}

	private static String actor(String actorSubject) {
		if (actorSubject == null || actorSubject.isBlank() || actorSubject.length() > 191) {
			throw new IllegalArgumentException("A valid actor subject is required");
		}
		return actorSubject;
	}

	private static String validCode(String code) {
		if (code == null) {
			throw new HoriTipPolicyException(
				HoriTipPolicyException.Reason.RULE_INVALID, "A Hori Tip code is required");
		}
		String normalized = code.strip();
		if (normalized.length() < 5 || normalized.length() > 80
			|| !CODE_PATTERN.matcher(normalized).matches()) {
			throw new HoriTipPolicyException(
				HoriTipPolicyException.Reason.RULE_INVALID, "The Hori Tip code format is invalid");
		}
		return normalized;
	}

	private static String requiredReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A status change reason is required");
		}
		String normalized = reason.strip();
		if (normalized.length() > 500) {
			throw new IllegalArgumentException("A status change reason is too long");
		}
		return normalized;
	}

	private static String normalizeFilter(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		String normalized = code.strip();
		if (normalized.length() > 80) {
			throw new IllegalArgumentException("Hori Tip code filter is too long");
		}
		return normalized;
	}

	private static HoriTipView view(
		HoriTipRecord record,
		boolean editableAllowed,
		Instant now
	) {
		HoriTipDraft draft = record.draft();
		boolean activeNow = record.status() == HoriTipStatus.ACTIVE
			&& (draft.validFrom() == null || !now.isBefore(draft.validFrom()))
			&& (draft.validUntil() == null || now.isBefore(draft.validUntil()));
		return new HoriTipView(
			record.id(),
			record.code(),
			SOURCE,
			record.status(),
			draft,
			record.version(),
			activeNow,
			editableAllowed && record.status() != HoriTipStatus.ARCHIVED,
			record.createdBySubject(),
			record.updatedBySubject(),
			record.activatedAt(),
			record.archivedAt(),
			record.createdAt(),
			record.updatedAt());
	}

	private static String encodeCursor(
		HoriTipStatus status,
		String code,
		Long destinationPlaceId,
		long beforeId
	) {
		String value = "1\t" + filterFingerprint(status, code, destinationPlaceId)
			+ "\t" + beforeId;
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static Long decodeCursor(
		String token,
		HoriTipStatus status,
		String code,
		Long destinationPlaceId
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidHoriTipCursorException();
		}
		try {
			String value = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = value.split("\t", -1);
			if (parts.length != 3
				|| !"1".equals(parts[0])
				|| !filterFingerprint(status, code, destinationPlaceId).equals(parts[1])) {
				throw new InvalidHoriTipCursorException();
			}
			long id = Long.parseLong(parts[2]);
			if (id <= 0) {
				throw new InvalidHoriTipCursorException();
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new InvalidHoriTipCursorException();
		}
	}

	private static String filterFingerprint(
		HoriTipStatus status,
		String code,
		Long destinationPlaceId
	) {
		String value = (status == null ? "ALL" : status.name()) + "\n"
			+ (code == null ? "" : code) + "\n"
			+ (destinationPlaceId == null ? "" : destinationPlaceId);
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	public record CreateCommand(String code, HoriTipDraft draft) {
	}

	public record UpdateCommand(int version, HoriTipDraft draft) {
	}

	public record StatusCommand(HoriTipStatusTarget status, int version, String reason) {
	}

	public record HoriTipPage(
		List<HoriTipView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record HoriTipView(
		long horiTipId,
		String code,
		String source,
		HoriTipStatus status,
		HoriTipDraft draft,
		int version,
		boolean activeNow,
		boolean editable,
		String createdBySubject,
		String updatedBySubject,
		Instant activatedAt,
		Instant archivedAt,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
