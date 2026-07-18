package koready_backend.onboarding.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

class InitialCandidatePlaceCatalogTest {

	private static final List<String> APPROVED_CONTENT_IDS = List.of(
		"126508",
		"132183",
		"129703",
		"125578",
		"128758",
		"125949",
		"506534",
		"264284",
		"1997221",
		"126435");

	@Test
	void containsTheApprovedTenPlacesInDisplayOrder() {
		List<InitialCandidatePlace> places = InitialCandidatePlaceCatalog.approved();

		assertEquals(10, places.size());
		assertEquals(APPROVED_CONTENT_IDS,
			places.stream().map(InitialCandidatePlace::ktoContentId).toList());
		assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(),
			places.stream().map(InitialCandidatePlace::displayOrder).toList());
		assertEquals(10, places.stream().map(InitialCandidatePlace::ktoContentId).distinct().count());
		assertTrue(places.stream().allMatch(place -> place.displayTags().size() <= 5));
	}

	@Test
	void coversAllSevenRegionsWithoutConcentratingMoreThanThreePlaces() {
		Map<ServiceRegionCode, Long> counts = counts(
			InitialCandidatePlace::serviceRegionCode);

		assertEquals(7, counts.size());
		assertTrue(counts.values().stream().allMatch(count -> count <= 3));
	}

	@Test
	void coversAllSevenTravelStylesWithoutConcentratingMoreThanTwoPlaces() {
		Map<TravelStyle, Long> counts = counts(InitialCandidatePlace::travelStyle);

		assertEquals(7, counts.size());
		assertTrue(counts.values().stream().allMatch(count -> count <= 2));
	}

	private static <T> Map<T, Long> counts(Function<InitialCandidatePlace, T> classifier) {
		return InitialCandidatePlaceCatalog.approved().stream()
			.collect(Collectors.groupingBy(classifier, Collectors.counting()));
	}
}
