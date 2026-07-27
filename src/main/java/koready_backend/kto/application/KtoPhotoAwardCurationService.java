package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.port.KtoPhotoAwardCurationRepository;
import koready_backend.kto.application.exception.KtoPhotoAwardNotFoundException;
import koready_backend.kto.application.port.KtoPhotoAwardCurationRepository.AuditRecord;
import koready_backend.kto.application.port.KtoPhotoAwardCurationRepository.MappingRecord;
import koready_backend.kto.application.port.KtoPhotoAwardCurationRepository.PhotoAwardQuery;
import koready_backend.kto.application.port.KtoPhotoAwardCurationRepository.PhotoAwardRecord;

@Service
public class KtoPhotoAwardCurationService {

	private static final int MAX_PAGE_SIZE = 100;

	private final KtoPhotoAwardCurationRepository repository;
	private final Clock clock;

	@Autowired
	public KtoPhotoAwardCurationService(
		KtoPhotoAwardCurationRepository repository
	) {
		this(repository, Clock.systemUTC());
	}

	KtoPhotoAwardCurationService(
		KtoPhotoAwardCurationRepository repository,
		Clock clock
	) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public PhotoAwardView approveMapping(
		String contentId,
		ApproveMappingCommand command,
		String actorSubject
	) {
		String normalizedContentId = required(contentId, 100, "contentId");
		String normalizedActor = required(actorSubject, 191, "actorSubject");
		String reason = required(command.reason(), 500, "reason");
		if (command.placeId() <= 0 || command.displayOrder() < 1
			|| command.displayOrder() > 20) {
			throw new IllegalArgumentException(
				"Photo award mapping target is invalid");
		}
		PhotoAwardRecord award = repository.findByContentId(normalizedContentId)
			.orElseThrow(KtoPhotoAwardNotFoundException::new);
		if (!repository.placeExists(command.placeId())) {
			throw new KtoPhotoAwardNotFoundException();
		}
		Instant now = clock.instant();
		repository.saveMapping(new MappingRecord(
			award.id(),
			award.contentId(),
			command.placeId(),
			command.displayOrder(),
			normalizedActor,
			reason,
			now));
		repository.recordAudit(new AuditRecord(
			normalizedActor,
			"PHOTO_AWARD_MAPPING_APPROVED",
			award.contentId(),
			reason,
			now));
		return view(repository.findByContentId(normalizedContentId).orElseThrow());
	}

	@Transactional
	public void removeMapping(
		String contentId,
		String reason,
		String actorSubject
	) {
		String normalizedContentId = required(contentId, 100, "contentId");
		String normalizedReason = required(reason, 500, "reason");
		String normalizedActor = required(actorSubject, 191, "actorSubject");
		if (repository.findByContentId(normalizedContentId).isEmpty()) {
			throw new KtoPhotoAwardNotFoundException();
		}
		repository.removeMapping(normalizedContentId);
		repository.recordAudit(new AuditRecord(
			normalizedActor,
			"PHOTO_AWARD_MAPPING_REMOVED",
			normalizedContentId,
			normalizedReason,
			clock.instant()));
	}

	@Transactional(readOnly = true)
	public PhotoAwardPage list(
		String query,
		Boolean mapped,
		long startAfterId,
		int size
	) {
		if (startAfterId < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
				"Photo award list request is invalid");
		}
		String normalizedQuery = query == null || query.isBlank()
			? null
			: required(query, 100, "query");
		List<PhotoAwardRecord> records = repository.findPage(
			new PhotoAwardQuery(
				normalizedQuery, mapped, startAfterId, size + 1));
		boolean hasMore = records.size() > size;
		List<PhotoAwardRecord> visible = records.subList(
			0, Math.min(size, records.size()));
		return new PhotoAwardPage(
			visible.stream().map(KtoPhotoAwardCurationService::view).toList(),
			hasMore && !visible.isEmpty()
				? Long.toString(visible.getLast().id())
				: null,
			hasMore);
	}

	private static PhotoAwardView view(PhotoAwardRecord record) {
		return new PhotoAwardView(
			record.contentId(),
			record.titleKo(),
			record.filmLocationKo(),
			record.keywordKo(),
			record.titleEn(),
			record.filmLocationEn(),
			record.keywordEn(),
			record.originalImageUrl(),
			record.thumbnailImageUrl(),
			record.copyrightType(),
			record.mappedPlaceId(),
			record.mappedPlaceTitleKo(),
			record.displayOrder(),
			record.approvedBySubject(),
			record.approvalReason(),
			record.approvedAt(),
			record.sourceCapturedAt());
	}

	private static String required(
		String value,
		int maxLength,
		String field
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"Photo award " + field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Photo award " + field + " is too long");
		}
		return normalized;
	}

	public record ApproveMappingCommand(
		long placeId,
		int displayOrder,
		String reason
	) {
	}

	public record PhotoAwardPage(
		List<PhotoAwardView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record PhotoAwardView(
		String contentId,
		String titleKo,
		String filmLocationKo,
		String keywordKo,
		String titleEn,
		String filmLocationEn,
		String keywordEn,
		String originalImageUrl,
		String thumbnailImageUrl,
		String copyrightType,
		Long mappedPlaceId,
		String mappedPlaceTitleKo,
		Integer displayOrder,
		String approvedBySubject,
		String approvalReason,
		Instant approvedAt,
		Instant sourceCapturedAt
	) {
	}
}
