package koready_backend.kto.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.exception.KtoEnglishReviewNotFoundException;
import koready_backend.kto.application.exception.KtoEnglishReviewSourceUnavailableException;
import koready_backend.kto.application.port.KtoEnglishReviewRepository;
import koready_backend.kto.application.port.KtoEnglishReviewRepository.ReviewCriteria;
import koready_backend.kto.application.port.KtoEnglishReviewRepository.ReviewDetailRecord;
import koready_backend.kto.application.port.KtoEnglishReviewRepository.ReviewSummaryRecord;
import koready_backend.kto.application.port.KtoEnglishReviewSourceReader;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishReviewDecision;
import koready_backend.kto.domain.KtoEnglishReviewStatus;

@Service
public class KtoEnglishReviewService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_CURSOR_LENGTH = 512;

	private final KtoEnglishReviewRepository repository;
	private final KtoEnglishReviewSourceReader sourceReader;

	public KtoEnglishReviewService(
		KtoEnglishReviewRepository repository,
		KtoEnglishReviewSourceReader sourceReader
	) {
		this.repository = repository;
		this.sourceReader = sourceReader;
	}

	@Transactional(readOnly = true)
	public ReviewPage list(ReviewQuery query) {
		validate(query);
		String fingerprint = fingerprint(
			name(query.status()), normalizedSearch(query.search()), String.valueOf(query.size()));
		Long beforeId = decodeCursor(query.cursor(), fingerprint);
		List<ReviewSummaryRecord> rows = repository.findPage(new ReviewCriteria(
			query.status(), normalizedSearch(query.search()), beforeId, query.size() + 1));
		boolean hasMore = rows.size() > query.size();
		List<ReviewSummaryRecord> visible =
			rows.subList(0, Math.min(query.size(), rows.size()));
		Map<Long, KtoEnglishPlaceItem> sources = readSources(visible);
		List<ReviewSummaryView> items = visible.stream()
			.map(row -> summary(row, sources.get(row.sourceRecordId())))
			.toList();
		String nextCursor = hasMore && !visible.isEmpty()
			? encodeCursor(fingerprint, visible.getLast().sourceRecordId())
			: null;
		return new ReviewPage(items, nextCursor, hasMore);
	}

	@Transactional(readOnly = true)
	public ReviewDetailView get(long sourceRecordId) {
		ReviewDetailRecord detail = load(sourceRecordId);
		KtoEnglishPlaceItem source = readSource(detail.summary());
		return detail(detail, source);
	}

	public ReviewDecisionView decide(long sourceRecordId, ReviewDecisionCommand command) {
		if (command == null || command.decision() == null
			|| command.expectedVersion() < 0
			|| command.reviewedBy() == null || command.reviewedBy().isBlank()
			|| command.reason() == null || command.reason().isBlank()
			|| command.reason().length() > 500) {
			throw new IllegalArgumentException("Invalid KTO English review decision");
		}
		if (command.decision() == KtoEnglishReviewDecision.MANUAL_CONFIRMED
			&& (command.selectedPlaceId() == null || command.selectedPlaceId() <= 0)) {
			throw new IllegalArgumentException("A selected place is required");
		}
		if (command.decision() == KtoEnglishReviewDecision.REJECTED
			&& command.selectedPlaceId() != null) {
			throw new IllegalArgumentException("A rejected source cannot select a place");
		}
		ReviewDetailRecord detail = load(sourceRecordId);
		KtoEnglishPlaceItem source = readSource(detail.summary());
		var decided = repository.review(new KtoEnglishReviewRepository.ReviewCommand(
			sourceRecordId,
			command.decision(),
			command.selectedPlaceId(),
			command.expectedVersion(),
			command.reviewedBy(),
			command.reason().strip(),
			source));
		return new ReviewDecisionView(
			decided.sourceRecordId(),
			decided.status(),
			decided.selectedPlaceId(),
			decided.version(),
			decided.reviewedBy(),
			decided.reason(),
			decided.decidedAt());
	}

	private ReviewDetailRecord load(long sourceRecordId) {
		if (sourceRecordId <= 0) {
			throw new IllegalArgumentException("Source record ID must be positive");
		}
		return repository.findBySourceRecordId(sourceRecordId)
			.orElseThrow(() -> new KtoEnglishReviewNotFoundException(sourceRecordId));
	}

	private Map<Long, KtoEnglishPlaceItem> readSources(List<ReviewSummaryRecord> rows) {
		Map<String, List<ReviewSummaryRecord>> grouped = new LinkedHashMap<>();
		for (ReviewSummaryRecord row : rows) {
			grouped.computeIfAbsent(row.storageKey(), ignored -> new ArrayList<>()).add(row);
		}
		Map<Long, KtoEnglishPlaceItem> result = new HashMap<>();
		for (var entry : grouped.entrySet()) {
			List<String> ids = entry.getValue().stream()
				.map(ReviewSummaryRecord::sourceContentId)
				.toList();
			Map<String, KtoEnglishPlaceItem> found;
			try {
				found = sourceReader.findAll(entry.getKey(), ids);
			} catch (RuntimeException exception) {
				found = Map.of();
			}
			for (ReviewSummaryRecord row : entry.getValue()) {
				KtoEnglishPlaceItem source = found.get(row.sourceContentId());
				if (source != null) {
					result.put(row.sourceRecordId(), source);
				}
			}
		}
		return Map.copyOf(result);
	}

	private KtoEnglishPlaceItem readSource(ReviewSummaryRecord summary) {
		try {
			KtoEnglishPlaceItem source = sourceReader.findAll(
					summary.storageKey(), List.of(summary.sourceContentId()))
				.get(summary.sourceContentId());
			if (source == null) {
				throw new KtoEnglishReviewSourceUnavailableException(
					summary.sourceRecordId());
			}
			return source;
		} catch (RuntimeException exception) {
			if (exception instanceof KtoEnglishReviewSourceUnavailableException unavailable) {
				throw unavailable;
			}
			throw new KtoEnglishReviewSourceUnavailableException(summary.sourceRecordId());
		}
	}

	private static ReviewSummaryView summary(
		ReviewSummaryRecord row,
		KtoEnglishPlaceItem source
	) {
		return new ReviewSummaryView(
			row.sourceRecordId(),
			row.sourceContentId(),
			source == null ? null : source.title(),
			source == null ? null : joinAddress(source.address1(), source.address2()),
			source == null ? null : source.primaryImageUrl(),
			source != null,
			row.status(),
			row.candidateCount(),
			row.decisionVersion(),
			row.selectedPlaceId(),
			row.capturedAt(),
			row.decidedAt());
	}

	private static ReviewDetailView detail(
		ReviewDetailRecord detail,
		KtoEnglishPlaceItem source
	) {
		ReviewSummaryRecord summary = detail.summary();
		return new ReviewDetailView(
			summary(summary, source),
			new SourceView(
				source.contentId(),
				source.oldContentId(),
				source.contentTypeId(),
				source.title(),
				source.address1(),
				source.address2(),
				source.primaryImageUrl(),
				source.thumbnailImageUrl(),
				source.longitude(),
				source.latitude(),
				source.modifiedTime(),
				source.showFlag(),
				source.sourceHash(),
				summary.rawSnapshotId()),
			detail.candidates().stream().map(candidate -> new CandidateView(
				candidate.placeId(),
				candidate.titleKo(),
				candidate.address(),
				candidate.imageUrl(),
				candidate.matchMethod(),
				candidate.confidence(),
				candidate.candidateCount(),
				candidate.imageCandidateCount(),
				candidate.coordinateCandidateCount(),
				candidate.evidenceConflict(),
				candidate.selected())).toList(),
			detail.audits().stream().map(audit -> new AuditView(
				audit.auditId(),
				audit.previousStatus(),
				audit.newStatus(),
				audit.previousPlaceId(),
				audit.selectedPlaceId(),
				audit.reviewedBy(),
				audit.reason(),
				audit.decisionVersion(),
				audit.createdAt())).toList());
	}

	private static void validate(ReviewQuery query) {
		if (query == null || query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Invalid KTO English review page parameters");
		}
		if (query.search() != null && query.search().strip().length() > 100) {
			throw new IllegalArgumentException("KTO English review search is too long");
		}
	}

	private static String normalizedSearch(String search) {
		return search == null || search.isBlank() ? null : search.strip();
	}

	private static String joinAddress(String first, String second) {
		return java.util.stream.Stream.of(first, second)
			.filter(value -> value != null && !value.isBlank())
			.collect(java.util.stream.Collectors.joining(" "));
	}

	private static String encodeCursor(String fingerprint, long beforeId) {
		String value = "1\t" + fingerprint + "\t" + beforeId;
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static Long decodeCursor(String token, String fingerprint) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new IllegalArgumentException("Invalid KTO English review cursor");
		}
		try {
			String value = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = value.split("\t", -1);
			if (parts.length != 3 || !"1".equals(parts[0])
				|| !fingerprint.equals(parts[1])) {
				throw new IllegalArgumentException("Invalid KTO English review cursor");
			}
			long id = Long.parseLong(parts[2]);
			if (id <= 0) {
				throw new IllegalArgumentException("Invalid KTO English review cursor");
			}
			return id;
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid KTO English review cursor");
		}
	}

	private static String fingerprint(String... values) {
		String value = String.join("\n", Arrays.stream(values)
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

	public record ReviewQuery(
		KtoEnglishReviewStatus status,
		String search,
		String cursor,
		int size
	) {
	}

	public record ReviewPage(
		List<ReviewSummaryView> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record ReviewSummaryView(
		long sourceRecordId,
		String sourceContentId,
		String titleEn,
		String addressEn,
		String primaryImageUrl,
		boolean sourceAvailable,
		KtoEnglishReviewStatus status,
		int candidateCount,
		int decisionVersion,
		Long selectedPlaceId,
		Instant capturedAt,
		Instant decidedAt
	) {
	}

	public record ReviewDetailView(
		ReviewSummaryView summary,
		SourceView source,
		List<CandidateView> candidates,
		List<AuditView> audits
	) {
	}

	public record SourceView(
		String contentId,
		String oldContentId,
		String contentTypeId,
		String title,
		String address1,
		String address2,
		String primaryImageUrl,
		String thumbnailImageUrl,
		String longitude,
		String latitude,
		String modifiedTime,
		String showFlag,
		String sourceHash,
		long rawSnapshotId
	) {
	}

	public record CandidateView(
		long placeId,
		String titleKo,
		String address,
		String imageUrl,
		String matchMethod,
		double confidence,
		int candidateCount,
		int imageCandidateCount,
		int coordinateCandidateCount,
		boolean evidenceConflict,
		boolean selected
	) {
	}

	public record AuditView(
		long auditId,
		KtoEnglishReviewStatus previousStatus,
		KtoEnglishReviewStatus newStatus,
		Long previousPlaceId,
		Long selectedPlaceId,
		String reviewedBy,
		String reason,
		int decisionVersion,
		Instant createdAt
	) {
	}

	public record ReviewDecisionCommand(
		KtoEnglishReviewDecision decision,
		Long selectedPlaceId,
		int expectedVersion,
		String reviewedBy,
		String reason
	) {
	}

	public record ReviewDecisionView(
		long sourceRecordId,
		KtoEnglishReviewStatus status,
		Long selectedPlaceId,
		int version,
		String reviewedBy,
		String reason,
		Instant decidedAt
	) {
	}
}
