package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoRelatedTourNotFoundException;
import koready_backend.kto.application.port.KtoRelatedTourCurationRepository;
import koready_backend.kto.application.port.KtoRelatedTourCurationRepository.AuditRecord;
import koready_backend.kto.application.port.KtoRelatedTourCurationRepository.MappingRecord;
import koready_backend.kto.application.port.KtoRelatedTourCurationRepository.RelatedTourQuery;
import koready_backend.kto.application.port.KtoRelatedTourCurationRepository.RelatedTourRecord;

@Service
public class KtoRelatedTourCurationService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> MATCH_STATUSES = Set.of(
		"UNMATCHED", "AUTO_CONFIRMED", "MANUAL_CONFIRMED");

	private final KtoRelatedTourCurationRepository repository;
	private final Clock clock;

	@Autowired
	public KtoRelatedTourCurationService(
		KtoRelatedTourCurationRepository repository
	) {
		this(repository, Clock.systemUTC());
	}

	KtoRelatedTourCurationService(
		KtoRelatedTourCurationRepository repository,
		Clock clock
	) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public RelatedTourView confirmMapping(
		long recordId,
		ConfirmMappingCommand command,
		String actorSubject
	) {
		if (recordId <= 0 || command == null
			|| command.sourcePlaceId() <= 0
			|| command.relatedPlaceId() <= 0
			|| command.sourcePlaceId() == command.relatedPlaceId()) {
			throw new IllegalArgumentException(
				"Related tour mapping target is invalid");
		}
		String actor = required(
			actorSubject, 191, "actorSubject");
		String reason = required(command.reason(), 500, "reason");
		repository.findById(recordId)
			.orElseThrow(KtoRelatedTourNotFoundException::new);
		if (!repository.placeExists(command.sourcePlaceId())
			|| !repository.placeExists(command.relatedPlaceId())) {
			throw new KtoRelatedTourNotFoundException();
		}
		Instant now = clock.instant();
		repository.saveMapping(new MappingRecord(
			recordId,
			command.sourcePlaceId(),
			command.relatedPlaceId(),
			actor,
			reason,
			now));
		repository.recordAudit(new AuditRecord(
			actor,
			"RELATED_TOUR_MAPPING_CONFIRMED",
			recordId,
			reason,
			now));
		return view(repository.findById(recordId).orElseThrow());
	}

	@Transactional
	public void removeMapping(
		long recordId,
		String reason,
		String actorSubject
	) {
		if (recordId <= 0) {
			throw new IllegalArgumentException(
				"Related tour record ID is invalid");
		}
		String normalizedReason = required(reason, 500, "reason");
		String actor = required(
			actorSubject, 191, "actorSubject");
		repository.findById(recordId)
			.orElseThrow(KtoRelatedTourNotFoundException::new);
		repository.removeMapping(recordId);
		repository.recordAudit(new AuditRecord(
			actor,
			"RELATED_TOUR_MAPPING_REMOVED",
			recordId,
			normalizedReason,
			clock.instant()));
	}

	@Transactional(readOnly = true)
	public RelatedTourPage list(
		String query,
		String matchStatus,
		long startAfterId,
		int size
	) {
		if (startAfterId < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
				"Related tour list request is invalid");
		}
		String normalizedQuery = query == null || query.isBlank()
			? null
			: required(query, 100, "query");
		String normalizedStatus =
			matchStatus == null || matchStatus.isBlank()
				? null
				: matchStatus.strip().toUpperCase(
					java.util.Locale.ROOT);
		if (normalizedStatus != null
			&& !MATCH_STATUSES.contains(normalizedStatus)) {
			throw new IllegalArgumentException(
				"Related tour match status is invalid");
		}
		List<RelatedTourRecord> records = repository.findPage(
			new RelatedTourQuery(
				normalizedQuery,
				normalizedStatus,
				startAfterId,
				size + 1));
		boolean hasMore = records.size() > size;
		List<RelatedTourRecord> visible = records.subList(
			0, Math.min(size, records.size()));
		return new RelatedTourPage(
			visible.stream()
				.map(KtoRelatedTourCurationService::view)
				.toList(),
			hasMore && !visible.isEmpty()
				? Long.toString(visible.getLast().id())
				: null,
			hasMore);
	}

	private static RelatedTourView view(RelatedTourRecord record) {
		return new RelatedTourView(
			record.id(),
			record.baseYearMonth(),
			record.sourceTourCode(),
			record.sourceName(),
			record.sourceRegionName(),
			record.sourceSignguName(),
			record.relatedTourCode(),
			record.relatedName(),
			record.relatedRegionName(),
			record.relatedSignguName(),
			record.categoryLarge(),
			record.categoryMedium(),
			record.categorySmall(),
			record.rank(),
			record.matchStatus(),
			record.sourcePlaceId(),
			record.sourcePlaceTitle(),
			record.relatedPlaceId(),
			record.relatedPlaceTitle(),
			record.confirmedBySubject(),
			record.confirmationReason(),
			record.confirmedAt(),
			record.sourceCapturedAt());
	}

	private static String required(
		String value,
		int maxLength,
		String field
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"Related tour " + field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(
				"Related tour " + field + " is too long");
		}
		return normalized;
	}

	public record ConfirmMappingCommand(
		long sourcePlaceId,
		long relatedPlaceId,
		String reason
	) {
	}

	public record RelatedTourPage(
		List<RelatedTourView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record RelatedTourView(
		long id,
		String baseYearMonth,
		String sourceTourCode,
		String sourceName,
		String sourceRegionName,
		String sourceSignguName,
		String relatedTourCode,
		String relatedName,
		String relatedRegionName,
		String relatedSignguName,
		String categoryLarge,
		String categoryMedium,
		String categorySmall,
		int rank,
		String matchStatus,
		Long sourcePlaceId,
		String sourcePlaceTitle,
		Long relatedPlaceId,
		String relatedPlaceTitle,
		String confirmedBySubject,
		String confirmationReason,
		Instant confirmedAt,
		Instant sourceCapturedAt
	) {
	}
}
