package koready_backend.kto.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KtoEnglishPlaceMatcher {

	private final Map<String, List<KtoEnglishPlaceCandidate>> imageCandidates;
	private final Map<String, List<KtoEnglishPlaceCandidate>> coordinateCandidates;

	public KtoEnglishPlaceMatcher(Collection<KtoEnglishPlaceCandidate> candidates) {
		imageCandidates = index(candidates, true);
		coordinateCandidates = index(candidates, false);
	}

	public KtoEnglishMatchDecision match(KtoEnglishPlaceItem source) {
		String imageKey = KtoEnglishMatchKeyFactory.imagePathHash(source.primaryImageUrl());
		String coordinateKey = KtoEnglishMatchKeyFactory.englishCoordinateContentTypeKey(
			source.longitude(), source.latitude(), source.contentTypeId());
		List<KtoEnglishPlaceCandidate> images = candidates(imageCandidates, imageKey);
		List<KtoEnglishPlaceCandidate> coordinates = candidates(coordinateCandidates, coordinateKey);

		if (images.size() == 1 && coordinates.size() <= 1
			&& (coordinates.isEmpty() || images.getFirst().placeId() == coordinates.getFirst().placeId())) {
			return decision(source, KtoEnglishMatchStatus.AUTO_CONFIRMED,
				KtoEnglishMatchMethod.IMAGE_PATH, images, images.size(), coordinates.size());
		}
		if (images.isEmpty() && coordinates.size() == 1) {
			return decision(source, KtoEnglishMatchStatus.AUTO_CONFIRMED,
				KtoEnglishMatchMethod.COORDINATE_CONTENT_TYPE, coordinates, 0, 1);
		}

		List<KtoEnglishPlaceCandidate> combined = combine(images, coordinates);
		if (!combined.isEmpty()) {
			return decision(source, KtoEnglishMatchStatus.REVIEW_REQUIRED,
				KtoEnglishMatchMethod.EVIDENCE_CONFLICT, combined, images.size(), coordinates.size());
		}
		return decision(source, KtoEnglishMatchStatus.UNMATCHED,
			KtoEnglishMatchMethod.NONE, List.of(), 0, 0);
	}

	private Map<String, List<KtoEnglishPlaceCandidate>> index(
		Collection<KtoEnglishPlaceCandidate> candidates,
		boolean image
	) {
		Map<String, List<KtoEnglishPlaceCandidate>> values = new HashMap<>();
		for (KtoEnglishPlaceCandidate candidate : candidates) {
			String key = image ? candidate.imagePathHash() : candidate.coordinateContentTypeKey();
			if (key != null) {
				values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
			}
		}
		values.replaceAll((key, items) -> items.stream()
			.sorted(Comparator.comparingLong(KtoEnglishPlaceCandidate::placeId))
			.toList());
		return Map.copyOf(values);
	}

	private List<KtoEnglishPlaceCandidate> candidates(
		Map<String, List<KtoEnglishPlaceCandidate>> index,
		String key
	) {
		return key == null ? List.of() : index.getOrDefault(key, List.of());
	}

	private List<KtoEnglishPlaceCandidate> combine(
		List<KtoEnglishPlaceCandidate> first,
		List<KtoEnglishPlaceCandidate> second
	) {
		Map<Long, KtoEnglishPlaceCandidate> unique = new LinkedHashMap<>();
		first.forEach(candidate -> unique.put(candidate.placeId(), candidate));
		second.forEach(candidate -> unique.put(candidate.placeId(), candidate));
		return unique.values().stream()
			.sorted(Comparator.comparingLong(KtoEnglishPlaceCandidate::placeId))
			.toList();
	}

	private KtoEnglishMatchDecision decision(
		KtoEnglishPlaceItem source,
		KtoEnglishMatchStatus status,
		KtoEnglishMatchMethod method,
		List<KtoEnglishPlaceCandidate> candidates,
		int imageCount,
		int coordinateCount
	) {
		return new KtoEnglishMatchDecision(
			source, status, method, candidates, imageCount, coordinateCount);
	}
}
