package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.exception.KtoEnglishReviewSourceUnavailableException;
import koready_backend.kto.application.port.KtoEnglishReviewRepository;
import koready_backend.kto.application.port.KtoEnglishReviewRepository.ReviewDetailRecord;
import koready_backend.kto.application.port.KtoEnglishReviewRepository.ReviewSummaryRecord;
import koready_backend.kto.application.port.KtoEnglishReviewSourceReader;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.domain.KtoEnglishReviewDecision;
import koready_backend.kto.domain.KtoEnglishReviewStatus;
import koready_backend.kto.domain.KtoEnglishSourceQuality;

@ExtendWith(MockitoExtension.class)
class KtoEnglishReviewServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T01:00:00Z");

	@Mock
	KtoEnglishReviewRepository repository;

	@Mock
	KtoEnglishReviewSourceReader sourceReader;

	KtoEnglishReviewService service;

	@BeforeEach
	void setUp() {
		service = new KtoEnglishReviewService(repository, sourceReader);
	}

	@Test
	void enrichesAReviewPageFromOneSnapshotRead() {
		ReviewSummaryRecord first = summary(32L, "eng-2");
		ReviewSummaryRecord second = summary(31L, "eng-1");
		when(repository.findPage(any())).thenReturn(List.of(first, second));
		when(sourceReader.findAll("kto/eng/page-1.json.gz", List.of("eng-2", "eng-1")))
			.thenReturn(Map.of(
				"eng-2", source("eng-2", "Second place"),
				"eng-1", source("eng-1", "First place")));

		var page = service.list(new KtoEnglishReviewService.ReviewQuery(
			null, null, null, 20));

		assertEquals(2, page.items().size());
		assertEquals("Second place", page.items().getFirst().titleEn());
		assertFalse(page.hasMore());
		verify(sourceReader).findAll(
			"kto/eng/page-1.json.gz", List.of("eng-2", "eng-1"));
	}

	@Test
	void filtersReviewsByComputedSourceQuality() {
		ReviewSummaryRecord russian = summary(32L, "eng-2");
		ReviewSummaryRecord english = summary(31L, "eng-1");
		when(repository.findPage(any())).thenReturn(List.of(russian, english));
		when(sourceReader.findAll("kto/eng/page-1.json.gz", List.of("eng-2", "eng-1")))
			.thenReturn(Map.of(
				"eng-2", source("eng-2", "Детский музей Самсунг"),
				"eng-1", source("eng-1", "Gyeongbokgung Palace")));

		var page = service.list(new KtoEnglishReviewService.ReviewQuery(
			null, KtoEnglishSourceQuality.USABLE, null, null, 20));

		assertEquals(1, page.items().size());
		assertEquals("eng-1", page.items().getFirst().sourceContentId());
		assertEquals(
			KtoEnglishSourceQuality.USABLE,
			page.items().getFirst().sourceQuality());
	}

	@Test
	void requiresTheRawSourceBeforeWritingADecision() {
		ReviewSummaryRecord summary = summary(31L, "eng-1");
		when(repository.findBySourceRecordId(31L))
			.thenReturn(Optional.of(new ReviewDetailRecord(summary, List.of(), List.of())));
		when(sourceReader.findAll(any(), any())).thenReturn(Map.of());

		assertThrows(KtoEnglishReviewSourceUnavailableException.class, () ->
			service.decide(31L, new KtoEnglishReviewService.ReviewDecisionCommand(
				KtoEnglishReviewDecision.REJECTED,
				null,
				0,
				"operator",
				"원본과 일치하지 않음")));
	}

	@Test
	void passesAuthenticatedActorAndExpectedVersionToRepository() {
		ReviewSummaryRecord summary = summary(31L, "eng-1");
		when(repository.findBySourceRecordId(31L))
			.thenReturn(Optional.of(new ReviewDetailRecord(summary, List.of(), List.of())));
		when(sourceReader.findAll(any(), any()))
			.thenReturn(Map.of("eng-1", source("eng-1", "English place")));
		when(repository.review(any())).thenReturn(
			new KtoEnglishReviewRepository.ReviewDecisionRecord(
				31L,
				KtoEnglishReviewStatus.REJECTED,
				null,
				3,
				"operator",
				"주소 불일치",
				NOW));

		var result = service.decide(
			31L,
			new KtoEnglishReviewService.ReviewDecisionCommand(
				KtoEnglishReviewDecision.REJECTED,
				null,
				2,
				"operator",
				"주소 불일치"));

		assertEquals(3, result.version());
		assertEquals("operator", result.reviewedBy());
		verify(repository).review(any());
	}

	private static ReviewSummaryRecord summary(long id, String contentId) {
		return new ReviewSummaryRecord(
			id,
			contentId,
			null,
			"a".repeat(64),
			11L,
			"kto/eng/page-1.json.gz",
			NOW,
			KtoEnglishReviewStatus.REVIEW_REQUIRED,
			2,
			0,
			null,
			null);
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
