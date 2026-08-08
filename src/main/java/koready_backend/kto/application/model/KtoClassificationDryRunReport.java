package koready_backend.kto.application.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import koready_backend.place.domain.TravelStyle;

public record KtoClassificationDryRunReport(
	String ruleVersion,
	long totalPlaces,
	long placesWithImage,
	long currentlyPublishedPlaces,
	long automaticallyUnclassifiedPlaces,
	long effectivelyUnclassifiedPlaces,
	long effectivelyClassifiedPlaces,
	long effectivelyClassifiedPlacesWithImage,
	long effectivelyClassifiedCurrentlyPublishedPlaces,
	long placesWithManualStyles,
	long manualStyleMappings,
	long multiStylePlaces,
	Map<TravelStyle, StyleCount> styleCounts,
	List<Overlap> overlaps
) {

	public KtoClassificationDryRunReport {
		styleCounts = Collections.unmodifiableMap(new LinkedHashMap<>(styleCounts));
		overlaps = List.copyOf(overlaps);
	}

	public record StyleCount(
		long automaticCandidates,
		long candidatesWithImage,
		long currentlyPublishedCandidates
	) {
	}

	public record Overlap(
		List<TravelStyle> styles,
		long count,
		List<Example> examples
	) {

		public Overlap {
			styles = List.copyOf(styles);
			examples = List.copyOf(examples);
		}
	}

	public record Example(
		long placeId,
		String ktoContentId,
		String title,
		List<TravelStyle> automaticStyles,
		List<TravelStyle> manualStyles,
		boolean hasImage,
		boolean currentlyPublished
	) {

		public Example {
			automaticStyles = List.copyOf(automaticStyles);
			manualStyles = List.copyOf(manualStyles);
		}
	}
}
