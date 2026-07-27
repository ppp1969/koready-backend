package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.model.KtoEnglishQualityBackfillRequest;
import koready_backend.kto.application.model.KtoEnglishQualityBackfillResult;
import koready_backend.kto.application.port.KtoEnglishQualityRepository;
import koready_backend.kto.application.port.KtoEnglishQualityRepository.QualityTarget;
import koready_backend.kto.application.port.KtoEnglishQualityRepository.QualityUpdate;
import koready_backend.kto.application.port.KtoEnglishReviewSourceReader;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishSourceQualityClassifier;

@Service
public class KtoEnglishQualityBackfillService {

	private final KtoEnglishQualityRepository repository;
	private final KtoEnglishReviewSourceReader sourceReader;
	private final Clock clock;
	private final KtoEnglishSourceQualityClassifier classifier =
		new KtoEnglishSourceQualityClassifier();

	@Autowired
	public KtoEnglishQualityBackfillService(
		KtoEnglishQualityRepository repository,
		KtoEnglishReviewSourceReader sourceReader
	) {
		this(repository, sourceReader, Clock.systemUTC());
	}

	KtoEnglishQualityBackfillService(
		KtoEnglishQualityRepository repository,
		KtoEnglishReviewSourceReader sourceReader,
		Clock clock
	) {
		this.repository = repository;
		this.sourceReader = sourceReader;
		this.clock = clock;
	}

	public KtoEnglishQualityBackfillResult backfill(
		KtoEnglishQualityBackfillRequest request
	) {
		List<QualityTarget> selected = repository.findUnclassified(
			request.startAfterSourceRecordId(), request.maxRecords() + 1);
		boolean hasMore = selected.size() > request.maxRecords();
		List<QualityTarget> targets = selected.subList(
			0, Math.min(request.maxRecords(), selected.size()));
		if (targets.isEmpty()) {
			return new KtoEnglishQualityBackfillResult(
				0, request.startAfterSourceRecordId(), false, request.autoContinue());
		}

		Map<String, List<QualityTarget>> bySnapshot = new LinkedHashMap<>();
		for (QualityTarget target : targets) {
			bySnapshot.computeIfAbsent(target.storageKey(), ignored -> new ArrayList<>())
				.add(target);
		}
		Instant classifiedAt = Instant.now(clock);
		for (var entry : bySnapshot.entrySet()) {
			List<String> contentIds = entry.getValue().stream()
				.map(QualityTarget::sourceContentId)
				.toList();
			Map<String, KtoEnglishPlaceItem> sources =
				sourceReader.findAll(entry.getKey(), contentIds);
			for (QualityTarget target : entry.getValue()) {
				KtoEnglishPlaceItem source = sources.get(target.sourceContentId());
				if (source == null || !target.sourceHash().equals(source.sourceHash())) {
					throw new IllegalStateException(
						"KTO English quality source is unavailable or changed");
				}
				var quality = classifier.classify(
					source.title(), joinAddress(source.address1(), source.address2()));
				repository.classify(new QualityUpdate(
					target.sourceRecordId(),
					target.sourceHash(),
					quality.quality(),
					quality.warnings(),
					classifiedAt,
					KtoEnglishSourceQualityClassifier.VERSION));
			}
		}
		return new KtoEnglishQualityBackfillResult(
			targets.size(),
			targets.getLast().sourceRecordId(),
			hasMore,
			request.autoContinue());
	}

	private static String joinAddress(String first, String second) {
		return java.util.stream.Stream.of(first, second)
			.filter(value -> value != null && !value.isBlank())
			.collect(java.util.stream.Collectors.joining(" "));
	}
}
