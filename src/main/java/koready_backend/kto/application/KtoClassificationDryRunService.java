package koready_backend.kto.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import koready_backend.kto.application.model.KtoClassificationCandidate;
import koready_backend.kto.application.model.KtoClassificationDryRunReport;
import koready_backend.kto.application.model.KtoClassificationDryRunReport.Example;
import koready_backend.kto.application.model.KtoClassificationDryRunReport.Overlap;
import koready_backend.kto.application.model.KtoClassificationDryRunReport.StyleCount;
import koready_backend.kto.application.port.KtoClassificationCandidateSource;
import koready_backend.kto.domain.KtoPlaceClassificationInput;
import koready_backend.kto.domain.KtoPlaceStyleRuleV1;
import koready_backend.place.domain.TravelStyle;

@Service
public class KtoClassificationDryRunService {

	private final KtoClassificationCandidateSource source;
	private final KtoPlaceStyleRuleV1 rule = new KtoPlaceStyleRuleV1();

	public KtoClassificationDryRunService(KtoClassificationCandidateSource source) {
		this.source = source;
	}

	public KtoClassificationDryRunReport run(int pageSize, int exampleLimit) {
		if (pageSize < 1 || pageSize > 1_000) {
			throw new IllegalArgumentException("Page size must be between 1 and 1000");
		}
		if (exampleLimit < 0 || exampleLimit > 20) {
			throw new IllegalArgumentException("Example limit must be between 0 and 20");
		}

		Accumulator accumulator = new Accumulator(exampleLimit);
		long afterPlaceId = 0;
		while (true) {
			List<KtoClassificationCandidate> page = source.findAfter(afterPlaceId, pageSize);
			if (page.isEmpty()) {
				break;
			}
			for (KtoClassificationCandidate candidate : page) {
				if (candidate.placeId() <= afterPlaceId) {
					throw new IllegalStateException(
						"Classification candidates must be ordered by ascending place id");
				}
				accumulator.add(candidate, rule.classify(input(candidate)));
				afterPlaceId = candidate.placeId();
			}
		}
		return accumulator.report();
	}

	private KtoPlaceClassificationInput input(KtoClassificationCandidate candidate) {
		return new KtoPlaceClassificationInput(
			candidate.contentTypeId(),
			candidate.classificationCode1(),
			candidate.classificationCode2(),
			candidate.classificationCode3());
	}

	private static final class Accumulator {

		private final int exampleLimit;
		private final EnumMap<TravelStyle, MutableStyleCount> styleCounts =
			new EnumMap<>(TravelStyle.class);
		private final Map<List<TravelStyle>, MutableOverlap> overlaps = new LinkedHashMap<>();
		private long totalPlaces;
		private long placesWithImage;
		private long currentlyPublishedPlaces;
		private long automaticallyUnclassifiedPlaces;
		private long effectivelyUnclassifiedPlaces;
		private long effectivelyClassifiedPlaces;
		private long effectivelyClassifiedPlacesWithImage;
		private long effectivelyClassifiedCurrentlyPublishedPlaces;
		private long placesWithManualStyles;
		private long manualStyleMappings;
		private long multiStylePlaces;

		private Accumulator(int exampleLimit) {
			this.exampleLimit = exampleLimit;
			for (TravelStyle style : TravelStyle.values()) {
				styleCounts.put(style, new MutableStyleCount());
			}
		}

		private void add(
			KtoClassificationCandidate candidate,
			Set<TravelStyle> automaticStyles
		) {
			totalPlaces++;
			if (candidate.hasImage()) {
				placesWithImage++;
			}
			if (candidate.currentlyPublished()) {
				currentlyPublishedPlaces++;
			}
			if (automaticStyles.isEmpty()) {
				automaticallyUnclassifiedPlaces++;
			}
			if (!candidate.manualStyles().isEmpty()) {
				placesWithManualStyles++;
				manualStyleMappings += candidate.manualStyles().size();
			}
			for (TravelStyle style : automaticStyles) {
				styleCounts.get(style).add(candidate);
			}

			EnumSet<TravelStyle> effectiveStyles = EnumSet.noneOf(TravelStyle.class);
			effectiveStyles.addAll(automaticStyles);
			effectiveStyles.addAll(candidate.manualStyles());
			if (effectiveStyles.isEmpty()) {
				effectivelyUnclassifiedPlaces++;
			} else {
				effectivelyClassifiedPlaces++;
				if (candidate.hasImage()) {
					effectivelyClassifiedPlacesWithImage++;
				}
				if (candidate.currentlyPublished()) {
					effectivelyClassifiedCurrentlyPublishedPlaces++;
				}
			}
			if (effectiveStyles.size() > 1) {
				multiStylePlaces++;
				List<TravelStyle> key = List.copyOf(effectiveStyles);
				overlaps.computeIfAbsent(key, ignored -> new MutableOverlap())
					.add(candidate, automaticStyles, exampleLimit);
			}
		}

		private KtoClassificationDryRunReport report() {
			EnumMap<TravelStyle, StyleCount> immutableStyleCounts =
				new EnumMap<>(TravelStyle.class);
			styleCounts.forEach((style, count) -> immutableStyleCounts.put(style, count.toView()));
			List<Overlap> immutableOverlaps = overlaps.entrySet().stream()
				.sorted(Comparator.comparing(entry -> styleKey(entry.getKey())))
				.map(entry -> entry.getValue().toView(entry.getKey()))
				.toList();
			return new KtoClassificationDryRunReport(
				KtoPlaceStyleRuleV1.VERSION,
				totalPlaces,
				placesWithImage,
				currentlyPublishedPlaces,
				automaticallyUnclassifiedPlaces,
				effectivelyUnclassifiedPlaces,
				effectivelyClassifiedPlaces,
				effectivelyClassifiedPlacesWithImage,
				effectivelyClassifiedCurrentlyPublishedPlaces,
				placesWithManualStyles,
				manualStyleMappings,
				multiStylePlaces,
				immutableStyleCounts,
				immutableOverlaps);
		}

		private String styleKey(List<TravelStyle> styles) {
			return String.join(",", styles.stream().map(Enum::name).toList());
		}
	}

	private static final class MutableStyleCount {

		private long automaticCandidates;
		private long candidatesWithImage;
		private long currentlyPublishedCandidates;

		private void add(KtoClassificationCandidate candidate) {
			automaticCandidates++;
			if (candidate.hasImage()) {
				candidatesWithImage++;
			}
			if (candidate.currentlyPublished()) {
				currentlyPublishedCandidates++;
			}
		}

		private StyleCount toView() {
			return new StyleCount(
				automaticCandidates,
				candidatesWithImage,
				currentlyPublishedCandidates);
		}
	}

	private static final class MutableOverlap {

		private long count;
		private final List<Example> examples = new ArrayList<>();

		private void add(
			KtoClassificationCandidate candidate,
			Set<TravelStyle> automaticStyles,
			int exampleLimit
		) {
			count++;
			if (examples.size() >= exampleLimit) {
				return;
			}
			examples.add(new Example(
				candidate.placeId(),
				candidate.ktoContentId(),
				candidate.title(),
				sortedStyles(automaticStyles),
				sortedStyles(candidate.manualStyles()),
				candidate.hasImage(),
				candidate.currentlyPublished()));
		}

		private List<TravelStyle> sortedStyles(Set<TravelStyle> styles) {
			return styles.stream().sorted().toList();
		}

		private Overlap toView(List<TravelStyle> styles) {
			return new Overlap(styles, count, examples);
		}
	}
}
