package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.model.KtoClassificationCandidate;
import koready_backend.kto.application.model.KtoClassificationDryRunReport;
import koready_backend.kto.application.port.KtoClassificationCandidateSource;
import koready_backend.place.domain.TravelStyle;

class KtoClassificationDryRunServiceTest {

	@Test
	void aggregatesAutomaticAndManualStylesWithoutWritingData() {
		RecordingSource source = new RecordingSource(List.of(
			candidate(1L, "39", "FD", "FD01", "FD010100", true, true, Set.of()),
			candidate(2L, "15", "EV", "EV01", "EV010100", false, false,
				Set.of(TravelStyle.DRAMA_LOCATION)),
			candidate(3L, "39", "FD", "FD05", "FD050100", true, true, Set.of()),
			candidate(4L, "12", "NA", "NA01", "NA010100", true, false,
				Set.of(TravelStyle.NATURE))));

		KtoClassificationDryRunReport report =
			new KtoClassificationDryRunService(source).run(2, 3);

		assertEquals(List.of(0L, 2L, 4L), source.afterPlaceIds);
		assertEquals(4, report.totalPlaces());
		assertEquals(3, report.placesWithImage());
		assertEquals(2, report.currentlyPublishedPlaces());
		assertEquals(1, report.automaticallyUnclassifiedPlaces());
		assertEquals(1, report.effectivelyUnclassifiedPlaces());
		assertEquals(3, report.effectivelyClassifiedPlaces());
		assertEquals(2, report.effectivelyClassifiedPlacesWithImage());
		assertEquals(1, report.effectivelyClassifiedCurrentlyPublishedPlaces());
		assertEquals(2, report.placesWithManualStyles());
		assertEquals(2, report.manualStyleMappings());
		assertEquals(1, report.multiStylePlaces());
		assertEquals(
			1,
			report.styleCounts().get(TravelStyle.LOCAL_FOOD).automaticCandidates());
		assertEquals(
			1,
			report.styleCounts().get(TravelStyle.LOCAL_FESTIVAL).automaticCandidates());
		assertEquals(
			1,
			report.styleCounts().get(TravelStyle.NATURE).automaticCandidates());
		assertEquals(
			1,
			report.overlaps().getFirst().count());
		assertEquals(
			List.of(TravelStyle.LOCAL_FESTIVAL, TravelStyle.DRAMA_LOCATION),
			report.overlaps().getFirst().styles());
		assertEquals(1, report.overlaps().getFirst().examples().size());
	}

	@Test
	void limitsExamplesDeterministically() {
		RecordingSource source = new RecordingSource(List.of(
			candidate(1L, "15", "EV", "EV01", "EV010100", true, true,
				Set.of(TravelStyle.DRAMA_LOCATION)),
			candidate(2L, "15", "EV", "EV01", "EV010100", true, true,
				Set.of(TravelStyle.DRAMA_LOCATION))));

		KtoClassificationDryRunReport report =
			new KtoClassificationDryRunService(source).run(10, 1);

		assertEquals(2, report.overlaps().getFirst().count());
		assertEquals(
			1L,
			report.overlaps().getFirst().examples().getFirst().placeId());
	}

	private KtoClassificationCandidate candidate(
		long placeId,
		String contentTypeId,
		String level1,
		String level2,
		String level3,
		boolean hasImage,
		boolean currentlyPublished,
		Set<TravelStyle> manualStyles
	) {
		return new KtoClassificationCandidate(
			placeId,
			"kto-" + placeId,
			"장소 " + placeId,
			contentTypeId,
			level1,
			level2,
			level3,
			hasImage,
			currentlyPublished,
			manualStyles);
	}

	private static final class RecordingSource implements KtoClassificationCandidateSource {

		private final List<KtoClassificationCandidate> candidates;
		private final List<Long> afterPlaceIds = new ArrayList<>();

		private RecordingSource(List<KtoClassificationCandidate> candidates) {
			this.candidates = candidates;
		}

		@Override
		public List<KtoClassificationCandidate> findAfter(long placeId, int limit) {
			afterPlaceIds.add(placeId);
			return candidates.stream()
				.filter(candidate -> candidate.placeId() > placeId)
				.limit(limit)
				.toList();
		}
	}
}
