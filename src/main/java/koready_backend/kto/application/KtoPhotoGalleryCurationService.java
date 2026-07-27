package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoPhotoGalleryNotFoundException;
import koready_backend.kto.application.port.KtoPhotoGalleryCurationRepository;
import koready_backend.kto.application.port.KtoPhotoGalleryCurationRepository.AuditRecord;
import koready_backend.kto.application.port.KtoPhotoGalleryCurationRepository.MappingRecord;
import koready_backend.kto.application.port.KtoPhotoGalleryCurationRepository.PhotoGalleryQuery;
import koready_backend.kto.application.port.KtoPhotoGalleryCurationRepository.PhotoGalleryRecord;

@Service
public class KtoPhotoGalleryCurationService {

	private static final int MAX_PAGE_SIZE = 100;

	private final KtoPhotoGalleryCurationRepository repository;
	private final Clock clock;

	@Autowired
	public KtoPhotoGalleryCurationService(
		KtoPhotoGalleryCurationRepository repository
	) {
		this(repository, Clock.systemUTC());
	}

	KtoPhotoGalleryCurationService(
		KtoPhotoGalleryCurationRepository repository,
		Clock clock
	) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public PhotoGalleryView approveMapping(
		String contentId,
		ApproveMappingCommand command,
		String actorSubject
	) {
		String normalizedContentId =
			required(contentId, 100, "contentId");
		String normalizedActor =
			required(actorSubject, 191, "actorSubject");
		String reason = required(command.reason(), 500, "reason");
		if (command.placeId() <= 0 || command.displayOrder() < 1
			|| command.displayOrder() > 20) {
			throw new IllegalArgumentException(
				"Photo gallery mapping target is invalid");
		}
		PhotoGalleryRecord image =
			repository.findByContentId(normalizedContentId)
				.orElseThrow(KtoPhotoGalleryNotFoundException::new);
		if (!repository.placeExists(command.placeId())) {
			throw new KtoPhotoGalleryNotFoundException();
		}
		Instant now = clock.instant();
		repository.saveMapping(new MappingRecord(
			image.id(),
			image.contentId(),
			command.placeId(),
			command.displayOrder(),
			normalizedActor,
			reason,
			now));
		repository.recordAudit(new AuditRecord(
			normalizedActor,
			"PHOTO_GALLERY_MAPPING_APPROVED",
			image.contentId(),
			reason,
			now));
		return view(repository.findByContentId(normalizedContentId)
			.orElseThrow());
	}

	@Transactional
	public void removeMapping(
		String contentId,
		String reason,
		String actorSubject
	) {
		String normalizedContentId =
			required(contentId, 100, "contentId");
		String normalizedReason = required(reason, 500, "reason");
		String normalizedActor =
			required(actorSubject, 191, "actorSubject");
		if (repository.findByContentId(normalizedContentId).isEmpty()) {
			throw new KtoPhotoGalleryNotFoundException();
		}
		repository.removeMapping(normalizedContentId);
		repository.recordAudit(new AuditRecord(
			normalizedActor,
			"PHOTO_GALLERY_MAPPING_REMOVED",
			normalizedContentId,
			normalizedReason,
			clock.instant()));
	}

	@Transactional(readOnly = true)
	public PhotoGalleryPage list(
		String query,
		Boolean mapped,
		long startAfterId,
		int size
	) {
		if (startAfterId < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
				"Photo gallery list request is invalid");
		}
		String normalizedQuery = query == null || query.isBlank()
			? null
			: required(query, 100, "query");
		List<PhotoGalleryRecord> records = repository.findPage(
			new PhotoGalleryQuery(
				normalizedQuery, mapped, startAfterId, size + 1));
		boolean hasMore = records.size() > size;
		List<PhotoGalleryRecord> visible = records.subList(
			0, Math.min(size, records.size()));
		return new PhotoGalleryPage(
			visible.stream()
				.map(KtoPhotoGalleryCurationService::view)
				.toList(),
			hasMore && !visible.isEmpty()
				? Long.toString(visible.getLast().id())
				: null,
			hasMore);
	}

	private static PhotoGalleryView view(
		PhotoGalleryRecord record
	) {
		return new PhotoGalleryView(
			record.contentId(),
			record.contentTypeId(),
			record.title(),
			record.photographyLocation(),
			record.photographyMonth(),
			record.photographer(),
			record.searchKeyword(),
			record.imageUrl(),
			record.rightsStatus(),
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
				"Photo gallery " + field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Photo gallery " + field + " is too long");
		}
		return normalized;
	}

	public record ApproveMappingCommand(
		long placeId,
		int displayOrder,
		String reason
	) {
	}

	public record PhotoGalleryPage(
		List<PhotoGalleryView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record PhotoGalleryView(
		String contentId,
		String contentTypeId,
		String title,
		String photographyLocation,
		String photographyMonth,
		String photographer,
		String searchKeyword,
		String imageUrl,
		String rightsStatus,
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
