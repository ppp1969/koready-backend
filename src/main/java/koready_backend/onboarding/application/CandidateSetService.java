package koready_backend.onboarding.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.onboarding.application.exception.CandidateSetConcurrentModificationException;
import koready_backend.onboarding.application.exception.CandidateSetCopySourceInvalidException;
import koready_backend.onboarding.application.exception.CandidateSetNotFoundException;
import koready_backend.onboarding.application.exception.InvalidCandidateSetCursorException;
import koready_backend.onboarding.application.port.CandidateSetRepository;
import koready_backend.onboarding.application.port.CandidateSetRepository.AuditRecord;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateItemRecord;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateSetRecord;
import koready_backend.onboarding.domain.CandidatePlaceReadiness;
import koready_backend.onboarding.domain.CandidateSetDraft;
import koready_backend.onboarding.domain.CandidateSetItemDraft;
import koready_backend.onboarding.domain.CandidateSetPolicyException;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Service
public class CandidateSetService {

	private static final int MAX_CURSOR_LENGTH = 256;

	private final CandidateSetRepository repository;
	private final Clock clock;
	private final Supplier<String> publicIdSupplier;

	@Autowired
	public CandidateSetService(CandidateSetRepository repository) {
		this(
			repository,
			Clock.systemUTC(),
			() -> "onb-" + UUID.randomUUID().toString().replace("-", ""));
	}

	CandidateSetService(
		CandidateSetRepository repository,
		Clock clock,
		Supplier<String> publicIdSupplier
	) {
		this.repository = repository;
		this.clock = clock;
		this.publicIdSupplier = publicIdSupplier;
	}

	@Transactional
	public AdminCandidateSet createDraft(
		CreateCandidateSetCommand command,
		String actorSubject,
		boolean editableAllowed
	) {
		Objects.requireNonNull(command, "Create command is required");
		List<CandidateSetItemDraft> copiedItems = List.of();
		if (command.copyFromSetId() != null && !command.copyFromSetId().isBlank()) {
			CandidateSetRecord source = repository.findByPublicId(command.copyFromSetId())
				.orElseThrow(() -> new CandidateSetNotFoundException(command.copyFromSetId()));
			if (source.status() != CandidateSetStatus.PUBLISHED) {
				throw new CandidateSetCopySourceInvalidException();
			}
			copiedItems = repository.findItems(source.id()).stream()
				.map(CandidateItemRecord::toDraft)
				.toList();
		}
		CandidateSetDraft draft = new CandidateSetDraft(command.title(), copiedItems);
		Instant now = clock.instant();
		CandidateSetRecord created = repository.insertDraft(
			publicIdSupplier.get(), draft.title(), now);
		if (!draft.items().isEmpty()) {
			repository.replaceDraft(created.id(), draft, now);
		}
		repository.recordAudit(new AuditRecord(
			actorSubject,
			"CURATION_SET_CREATED",
			created.publicId(),
			null,
			CandidateSetStatus.DRAFT,
			now));
		return admin(load(created.publicId()), editableAllowed);
	}

	@Transactional
	public AdminCandidateSet updateDraft(
		String candidateSetId,
		UpdateCandidateSetCommand command,
		String actorSubject,
		boolean editableAllowed
	) {
		CandidateSetRecord existing = lock(candidateSetId);
		existing.status().requireEditable();
		CandidateSetDraft draft = new CandidateSetDraft(command.title(), command.items());
		Instant now = clock.instant();
		repository.replaceDraft(existing.id(), draft, now);
		repository.recordAudit(new AuditRecord(
			actorSubject,
			"CURATION_SET_UPDATED",
			candidateSetId,
			CandidateSetStatus.DRAFT,
			CandidateSetStatus.DRAFT,
			now));
		return admin(load(candidateSetId), editableAllowed);
	}

	@Transactional
	public AdminCandidateSet publish(
		String candidateSetId,
		String actorSubject,
		boolean editableAllowed
	) {
		CandidateSetRecord existing = lock(candidateSetId);
		existing.status().requireEditable();
		List<CandidateItemRecord> itemRecords = repository.findItems(existing.id());
		List<CandidateSetItemDraft> items = itemRecords.stream()
			.map(CandidateItemRecord::toDraft)
			.toList();
		CandidateSetDraft draft = new CandidateSetDraft(existing.title(), items);
		Map<Long, CandidatePlaceReadiness> readiness = repository.findPlaceReadiness(items);
		draft.requirePublishable(readiness);

		Instant now = clock.instant();
		if (!repository.markPublished(existing.id(), actorSubject, now)) {
			throw new CandidateSetConcurrentModificationException();
		}
		repository.replaceCurrent(existing.id(), now);
		repository.recordAudit(new AuditRecord(
			actorSubject,
			"CURATION_SET_PUBLISHED",
			candidateSetId,
			CandidateSetStatus.DRAFT,
			CandidateSetStatus.PUBLISHED,
			now));
		return admin(load(candidateSetId), editableAllowed);
	}

	@Transactional
	public AdminCandidateSet archive(
		String candidateSetId,
		String actorSubject,
		boolean editableAllowed
	) {
		CandidateSetRecord existing = lock(candidateSetId);
		if (existing.status() == CandidateSetStatus.ARCHIVED) {
			throw new CandidateSetPolicyException(
				CandidateSetPolicyException.Reason.NOT_EDITABLE,
				"Archived candidate sets cannot change state");
		}
		Instant now = clock.instant();
		if (!repository.markArchived(existing.id(), actorSubject, now)) {
			throw new CandidateSetConcurrentModificationException();
		}
		repository.clearCurrent(existing.id());
		repository.recordAudit(new AuditRecord(
			actorSubject,
			"CURATION_SET_ARCHIVED",
			candidateSetId,
			existing.status(),
			CandidateSetStatus.ARCHIVED,
			now));
		return admin(load(candidateSetId), editableAllowed);
	}

	@Transactional(readOnly = true)
	public AdminCandidateSet getAdmin(String candidateSetId, boolean editableAllowed) {
		return admin(load(candidateSetId), editableAllowed);
	}

	@Transactional(readOnly = true)
	public CurrentCandidateSet getCurrent(PlaceLanguage language) {
		CandidateSetRecord set = repository.findCurrent()
			.orElseThrow(() -> new CandidateSetNotFoundException("current"));
		List<CurrentCandidateItem> items = repository.findItems(set.id()).stream()
			.map(item -> currentItem(item, language))
			.toList();
		return new CurrentCandidateSet(
			set.publicId(),
			set.version(),
			set.status(),
			set.publishedAt(),
			1,
			3,
			items);
	}

	@Transactional(readOnly = true)
	public CandidateSetPage list(
		CandidateSetStatus status,
		String cursorToken,
		int size
	) {
		Long beforeId = decodeCursor(cursorToken, status);
		List<CandidateSetRecord> rows = repository.findPage(status, beforeId, size + 1);
		boolean hasMore = rows.size() > size;
		List<CandidateSetRecord> visible = rows.subList(0, Math.min(size, rows.size()));
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor(status, visible.getLast().id())
			: null;
		return new CandidateSetPage(
			visible.stream().map(CandidateSetService::summary).toList(),
			nextCursor,
			hasMore);
	}

	private CandidateSetRecord lock(String candidateSetId) {
		return repository.findByPublicIdForUpdate(candidateSetId)
			.orElseThrow(() -> new CandidateSetNotFoundException(candidateSetId));
	}

	private CandidateSetRecord load(String candidateSetId) {
		return repository.findByPublicId(candidateSetId)
			.orElseThrow(() -> new CandidateSetNotFoundException(candidateSetId));
	}

	private AdminCandidateSet admin(CandidateSetRecord set, boolean editableAllowed) {
		return new AdminCandidateSet(
			set.publicId(),
			set.title(),
			set.version(),
			set.status(),
			set.itemCount(),
			set.current(),
			set.publishedAt(),
			set.createdAt(),
			set.updatedAt(),
			set.archivedAt(),
			set.status() == CandidateSetStatus.DRAFT && editableAllowed,
			parseUserId(set.publishedBySubject()),
			repository.findItems(set.id()).stream()
				.map(CandidateSetService::adminItem)
				.toList());
	}

	private static CandidateSetSummary summary(CandidateSetRecord set) {
		return new CandidateSetSummary(
			set.publicId(),
			set.title(),
			set.version(),
			set.status(),
			set.itemCount(),
			set.current(),
			set.publishedAt(),
			set.createdAt(),
			set.updatedAt());
	}

	private static AdminCandidateItem adminItem(CandidateItemRecord item) {
		return new AdminCandidateItem(
			item.placeId(),
			item.titleKo(),
			item.titleEn(),
			item.representativeImageId(),
			item.imageUrl(),
			item.displayOrder(),
			item.curatorMessageKo(),
			item.curatorMessageEn(),
			item.displayTags(),
			item.editorNote(),
			item.readiness().ready(),
			item.readiness().reasons());
	}

	private static CurrentCandidateItem currentItem(
		CandidateItemRecord item,
		PlaceLanguage language
	) {
		return new CurrentCandidateItem(
			item.placeId(),
			localized(language, item.titleKo(), item.titleEn()),
			item.imageUrl(),
			item.serviceRegionCode(),
			localized(language, item.serviceRegionNameKo(), item.serviceRegionNameEn()),
			item.travelStyle(),
			item.displayTags(),
			localized(language, item.curatorMessageKo(), item.curatorMessageEn()),
			item.displayOrder());
	}

	private static String localized(PlaceLanguage language, String korean, String english) {
		return language == PlaceLanguage.EN && english != null && !english.isBlank()
			? english : korean;
	}

	private static Long parseUserId(String subject) {
		if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) {
			return null;
		}
		try {
			return Long.valueOf(subject);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String encodeCursor(CandidateSetStatus status, long beforeId) {
		String filter = status == null ? "ALL" : status.name();
		String value = "1\t" + filter + "\t" + beforeId;
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static Long decodeCursor(String token, CandidateSetStatus status) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidCandidateSetCursorException();
		}
		try {
			String value = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = value.split("\t", -1);
			String expectedFilter = status == null ? "ALL" : status.name();
			if (parts.length != 3
				|| !"1".equals(parts[0])
				|| !expectedFilter.equals(parts[1])) {
				throw new InvalidCandidateSetCursorException();
			}
			long id = Long.parseLong(parts[2]);
			if (id <= 0) {
				throw new InvalidCandidateSetCursorException();
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new InvalidCandidateSetCursorException();
		}
	}

	public record CreateCandidateSetCommand(String title, String copyFromSetId) {
	}

	public record UpdateCandidateSetCommand(
		String title,
		List<CandidateSetItemDraft> items
	) {
		public UpdateCandidateSetCommand {
			items = List.copyOf(items);
		}
	}

	public record CandidateSetPage(
		List<CandidateSetSummary> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record CandidateSetSummary(
		String candidateSetId,
		String title,
		int version,
		CandidateSetStatus status,
		int itemCount,
		boolean current,
		Instant publishedAt,
		Instant createdAt,
		Instant updatedAt
	) {
	}

	public record AdminCandidateSet(
		String candidateSetId,
		String title,
		int version,
		CandidateSetStatus status,
		int itemCount,
		boolean current,
		Instant publishedAt,
		Instant createdAt,
		Instant updatedAt,
		Instant archivedAt,
		boolean editable,
		Long publishedByUserId,
		List<AdminCandidateItem> items
	) {
	}

	public record AdminCandidateItem(
		long placeId,
		String titleKo,
		String titleEn,
		Long representativeImageId,
		String imageUrl,
		int displayOrder,
		String curatorMessageKo,
		String curatorMessageEn,
		List<String> displayTags,
		String editorNote,
		boolean placeReady,
		List<String> notReadyReasons
	) {
	}

	public record CurrentCandidateSet(
		String candidateSetId,
		int version,
		CandidateSetStatus status,
		Instant publishedAt,
		int minSelection,
		int maxSelection,
		List<CurrentCandidateItem> items
	) {
	}

	public record CurrentCandidateItem(
		long placeId,
		String title,
		String imageUrl,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		TravelStyle travelStyle,
		List<String> tags,
		String curatorMessage,
		int displayOrder
	) {
	}
}
