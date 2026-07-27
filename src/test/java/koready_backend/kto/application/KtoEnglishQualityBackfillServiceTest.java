package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.model.KtoEnglishQualityBackfillRequest;
import koready_backend.kto.application.port.KtoEnglishQualityRepository;
import koready_backend.kto.application.port.KtoEnglishQualityRepository.QualityTarget;
import koready_backend.kto.application.port.KtoEnglishQualityRepository.QualityUpdate;
import koready_backend.kto.application.port.KtoEnglishReviewSourceReader;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishSourceQuality;

@ExtendWith(MockitoExtension.class)
class KtoEnglishQualityBackfillServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

	@Mock
	KtoEnglishQualityRepository repository;

	@Mock
	KtoEnglishReviewSourceReader sourceReader;

	KtoEnglishQualityBackfillService service;

	@BeforeEach
	void setUp() {
		service = new KtoEnglishQualityBackfillService(
			repository,
			sourceReader,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void readsEachSnapshotOnceAndPersistsClassifications() {
		List<QualityTarget> targets = List.of(
			target(11L, "eng-11"),
			target(12L, "eng-12"));
		when(repository.findUnclassified(0L, 3)).thenReturn(targets);
		when(sourceReader.findAll(
			"kto/eng/page-1.json.gz",
			List.of("eng-11", "eng-12")))
			.thenReturn(Map.of(
				"eng-11", source("eng-11", "Gyeongbokgung Palace"),
				"eng-12", source("eng-12", "Государственный музей")));

		var result = service.backfill(
			new KtoEnglishQualityBackfillRequest(0L, 2, false));

		assertEquals(2, result.processedRecords());
		assertEquals(12L, result.lastProcessedSourceRecordId());
		assertFalse(result.hasMore());
		ArgumentCaptor<QualityUpdate> update =
			ArgumentCaptor.forClass(QualityUpdate.class);
		verify(repository, org.mockito.Mockito.times(2)).classify(update.capture());
		assertEquals(
			List.of(
				KtoEnglishSourceQuality.USABLE,
				KtoEnglishSourceQuality.NON_ENGLISH_SUSPECTED),
			update.getAllValues().stream().map(QualityUpdate::quality).toList());
		assertEquals(NOW, update.getAllValues().getFirst().classifiedAt());
	}

	@Test
	void doesNotMarkAMissingSnapshotItemAsClassified() {
		when(repository.findUnclassified(0L, 2))
			.thenReturn(List.of(target(11L, "eng-11")));
		when(sourceReader.findAll(any(), any())).thenReturn(Map.of());

		assertThrows(IllegalStateException.class, () -> service.backfill(
			new KtoEnglishQualityBackfillRequest(0L, 1, false)));

		verify(repository, never()).classify(any());
	}

	private static QualityTarget target(long id, String contentId) {
		return new QualityTarget(
			id,
			contentId,
			"a".repeat(64),
			"kto/eng/page-1.json.gz");
	}

	private static KtoEnglishPlaceItem source(String contentId, String title) {
		return new KtoEnglishPlaceItem(
			contentId,
			null,
			"12",
			title,
			"88 Test-ro",
			"Seoul",
			"https://example.com/source.jpg",
			null,
			"126.98",
			"37.57",
			"20260727010000",
			"1",
			"a".repeat(64));
	}
}
