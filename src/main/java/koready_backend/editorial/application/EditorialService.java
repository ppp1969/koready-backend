package koready_backend.editorial.application;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.editorial.application.port.EditorialRepository;
import koready_backend.editorial.application.port.EditorialRepository.CandidateQuery;
import koready_backend.editorial.application.exception.EditorialPlaceNotFoundException;
import koready_backend.editorial.application.port.EditorialRepository.EnqueueCommand;
import koready_backend.editorial.application.port.EditorialRepository.EnqueueRecord;
import koready_backend.editorial.application.port.EditorialRepository.JobQuery;
import koready_backend.editorial.application.port.EditorialRepository.ReadyContentRecord;
import koready_backend.editorial.application.port.EditorialRepository.VisibilityCommand;
import koready_backend.editorial.application.port.EditorialRepository.PriorityCommand;
import koready_backend.editorial.application.port.EditorialRepository.ImageOrderCommand;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialCandidateStatusFilter;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.EditorialLanguage;
import koready_backend.editorial.domain.TourismPurposeTag;
import koready_backend.editorial.domain.EditorialCandidateRegionFilter;
import koready_backend.editorial.domain.EditorialCandidateSourceTrack;

@Service
public class EditorialService {

	private static final int MAX_PAGE_SIZE = 100;

	private final EditorialRepository repository;
	private final EditorialProperties properties;
	private final Clock clock;

	@Autowired
	public EditorialService(EditorialRepository repository, EditorialProperties properties) {
		this(repository, properties, Clock.systemUTC());
	}

	EditorialService(
		EditorialRepository repository,
		EditorialProperties properties,
		Clock clock
	) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public JobView enqueueByAdmin(long placeId, String actorSubject) {
		return enqueue(
			placeId,
			EditorialTriggerType.PM_CURATED,
			EditorialJobPriority.HIGH,
			required(actorSubject, "actorSubject"));
	}

	@Transactional
	public PublicEditorial findOrEnqueue(
		long placeId,
		EditorialLanguage language,
		String userSubject
	) {
		ReadyContentRecord ready = repository
			.findReady(placeId, language, properties.promptVersion())
			.orElse(null);
		if (ready != null) {
			return new PublicEditorial(
				EditorialJobStatus.READY, true, content(ready));
		}
		JobView job = enqueue(
			placeId,
			EditorialTriggerType.USER_DETAIL,
			EditorialJobPriority.NORMAL,
			optional(userSubject));
		return new PublicEditorial(job.status(), false, null);
	}

	@Transactional(readOnly = true)
	public CandidatePage candidates(
		String query,
		EditorialCandidateStatusFilter status,
		EditorialCandidateRegionFilter region,
		Boolean hasKoreanOverview,
		Boolean queueEligible,
		EditorialCandidateSourceTrack sourceTrack,
		long startAfterPlaceId,
		int size
	) {
		validatePage(startAfterPlaceId, size);
		List<EditorialRepository.CandidateRecord> records = repository.findCandidates(
			new CandidateQuery(
				optional(query), status, region, hasKoreanOverview, queueEligible, sourceTrack,
				startAfterPlaceId, size + 1));
		long totalCount = repository.countCandidates(new CandidateQuery(
			optional(query), status, region, hasKoreanOverview, queueEligible, sourceTrack, 0L, 1));
		boolean hasMore = records.size() > size;
		List<CandidateView> items = records.subList(0, Math.min(size, records.size()))
			.stream().map(CandidateView::from).toList();
		return new CandidatePage(
			items,
			hasMore && !items.isEmpty()
			? Long.toString(startAfterPlaceId + items.size()) : null,
			hasMore, totalCount);
	}

	@Transactional(readOnly = true)
	public CandidateDetailView candidate(long placeId) {
		if (placeId <= 0) {
			throw new IllegalArgumentException("placeId must be positive");
		}
		var candidate = repository.findCandidate(placeId)
			.orElseThrow(() -> new EditorialPlaceNotFoundException(placeId));
		return new CandidateDetailView(
			candidate.placeId(), candidate.titleKo(), candidate.titleEn(),
			candidate.overviewKo(), candidate.address(), candidate.region(),
			candidate.imageUrls(), candidate.images().stream()
				.map(image -> new PlaceImageView(
					image.imageId(), image.imageUrl(), image.displayOrder(),
					image.displayOrder() == 1))
				.toList(),
			candidate.travelStyles(), candidate.sourceTrack(), candidate.hasTrustedEnglish(),
			candidate.active(),
			candidate.showFlag(), candidate.active() && candidate.showFlag(),
			candidate.curationPriority(), candidate.status(),
			candidate.requestedAt());
	}

	@Transactional(readOnly = true)
	public JobPage jobs(EditorialJobStatus status, long startAfterId, int size) {
		validatePage(startAfterId, size);
		List<EditorialRepository.JobRecord> records = repository.findJobs(
			new JobQuery(status, startAfterId, size + 1));
		boolean hasMore = records.size() > size;
		List<JobDetailView> items = records.subList(0, Math.min(size, records.size()))
			.stream().map(JobDetailView::from).toList();
		return new JobPage(
			items,
			hasMore && !items.isEmpty()
				? Long.toString(items.getLast().id()) : null,
			hasMore);
	}

	@Transactional
	public PlaceVisibilityView updateVisibility(
		long placeId,
		boolean visible,
		String actorSubject
	) {
		if (placeId <= 0) {
			throw new IllegalArgumentException("placeId must be positive");
		}
		var record = repository.updateVisibility(new VisibilityCommand(
			placeId, visible, required(actorSubject, "actorSubject"), clock.instant()))
			.orElseThrow(() -> new EditorialPlaceNotFoundException(placeId));
		return new PlaceVisibilityView(
			record.placeId(), record.active(), record.showFlag(), record.visible(),
			record.updatedAt());
	}

	@Transactional
	public PlacePriorityView updateCurationPriority(
		long placeId,
		int priority,
		String actorSubject
	) {
		if (placeId <= 0 || priority < 0 || priority > 1000) {
			throw new IllegalArgumentException("Place priority request is invalid");
		}
		var record = repository.updateCurationPriority(new PriorityCommand(
			placeId, priority, required(actorSubject, "actorSubject"), clock.instant()))
			.orElseThrow(() -> new EditorialPlaceNotFoundException(placeId));
		return new PlacePriorityView(record.placeId(), record.priority(), record.updatedAt());
	}

	@Transactional
	public PlaceImageOrderView reorderImages(
		long placeId,
		List<Long> imageIds,
		String actorSubject
	) {
		if (placeId <= 0 || imageIds == null || imageIds.isEmpty()
			|| imageIds.size() > 100 || imageIds.stream().anyMatch(id -> id == null || id <= 0)
			|| imageIds.stream().distinct().count() != imageIds.size()) {
			throw new IllegalArgumentException("Place image order request is invalid");
		}
		var record = repository.reorderImages(new ImageOrderCommand(
			placeId, List.copyOf(imageIds), required(actorSubject, "actorSubject"), clock.instant()))
			.orElseThrow(() -> new EditorialPlaceNotFoundException(placeId));
		List<PlaceImageView> images = java.util.stream.IntStream.range(0, record.images().size())
			.mapToObj(index -> {
				var image = record.images().get(index);
				return new PlaceImageView(
					image.imageId(), image.imageUrl(), image.displayOrder(), index == 0);
			})
			.toList();
		return new PlaceImageOrderView(record.placeId(), images, record.updatedAt());
	}

	public boolean publicationFilterEnabled() {
		return properties.publicationFilterEnabled();
	}

	private JobView enqueue(
		long placeId,
		EditorialTriggerType trigger,
		EditorialJobPriority priority,
		String subject
	) {
		if (placeId <= 0) {
			throw new IllegalArgumentException("placeId must be positive");
		}
		EnqueueRecord record = repository.enqueue(new EnqueueCommand(
			placeId,
			properties.promptVersion(),
			trigger,
			priority,
			subject,
			clock.instant()));
		return JobView.from(record);
	}

	private static EditorialContent content(ReadyContentRecord record) {
		return new EditorialContent(
			record.topic(),
			record.oneLineDescription(),
			record.shortIntroduction(),
			record.enjoyPoints(),
			record.tags(),
			record.promptVersion());
	}

	private static void validatePage(long cursor, int size) {
		if (cursor < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Editorial page request is invalid");
		}
	}

	private static String required(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value.strip();
	}

	private static String optional(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	public record PublicEditorial(
		EditorialJobStatus status,
		boolean ready,
		EditorialContent content
	) {
	}

	public record EditorialContent(
		String topic,
		String oneLineDescription,
		String shortIntroduction,
		List<String> enjoyPoints,
		List<TourismPurposeTag> tags,
		String contentVersion
	) {
		public EditorialContent {
			enjoyPoints = List.copyOf(enjoyPoints);
			tags = List.copyOf(tags);
		}
	}

	public record JobView(
		String jobId,
		long placeId,
		EditorialJobStatus status,
		EditorialJobPriority priority,
		EditorialTriggerType triggerType,
		java.time.Instant requestedAt,
		boolean created
	) {
		static JobView from(EnqueueRecord record) {
			return new JobView(
				record.jobId(), record.placeId(), record.status(), record.priority(),
				record.triggerType(), record.requestedAt(), record.created());
		}
	}

	public record CandidatePage(
		List<CandidateView> items,
		String nextCursor,
		boolean hasMore,
		long totalCount
	) {
	}

	public record CandidateView(
		long placeId,
		String titleKo,
		String titleEn,
		String region,
		String imageUrl,
		boolean hasKoreanOverview,
		boolean queueEligible,
		EditorialCandidateSourceTrack sourceTrack,
		boolean hasTrustedEnglish,
		boolean active,
		boolean showFlag,
		boolean visible,
		int curationPriority,
		EditorialJobStatus status,
		java.time.Instant requestedAt
	) {
		static CandidateView from(EditorialRepository.CandidateRecord record) {
			return new CandidateView(
				record.placeId(), record.titleKo(), record.titleEn(), record.region(),
				record.imageUrl(), record.hasKoreanOverview(), record.queueEligible(),
				record.sourceTrack(), record.hasTrustedEnglish(),
				record.active(), record.showFlag(), record.active() && record.showFlag(),
				record.curationPriority(), record.status(),
				record.requestedAt());
		}
	}

	public record JobPage(List<JobDetailView> items, String nextCursor, boolean hasMore) {
	}

	public record CandidateDetailView(
		long placeId,
		String titleKo,
		String titleEn,
		String overviewKo,
		String address,
		String region,
		List<String> imageUrls,
		List<PlaceImageView> images,
		List<String> travelStyles,
		EditorialCandidateSourceTrack sourceTrack,
		boolean hasTrustedEnglish,
		boolean active,
		boolean showFlag,
		boolean visible,
		int curationPriority,
		EditorialJobStatus status,
		java.time.Instant requestedAt
	) {
		public CandidateDetailView {
			imageUrls = List.copyOf(imageUrls);
			images = List.copyOf(images);
			travelStyles = List.copyOf(travelStyles);
		}
	}

	public record JobDetailView(
		long id,
		String jobId,
		long placeId,
		EditorialJobStatus status,
		EditorialJobPriority priority,
		EditorialTriggerType triggerType,
		int attemptCount,
		String errorCode,
		String errorMessage,
		java.time.Instant requestedAt,
		java.time.Instant startedAt,
		java.time.Instant completedAt
	) {
		static JobDetailView from(EditorialRepository.JobRecord record) {
			return new JobDetailView(
				record.id(), record.jobId(), record.placeId(), record.status(),
				record.priority(), record.triggerType(), record.attemptCount(),
				record.errorCode(), record.errorMessage(), record.requestedAt(),
				record.startedAt(), record.completedAt());
		}
	}

	public record PlaceVisibilityView(
		long placeId,
		boolean active,
		boolean showFlag,
		boolean visible,
		java.time.Instant updatedAt
	) {
	}

	public record PlacePriorityView(long placeId, int priority, java.time.Instant updatedAt) {
	}

	public record PlaceImageView(
		long imageId,
		String imageUrl,
		int displayOrder,
		boolean thumbnail
	) {
	}

	public record PlaceImageOrderView(
		long placeId,
		List<PlaceImageView> images,
		java.time.Instant updatedAt
	) {
	}
}
