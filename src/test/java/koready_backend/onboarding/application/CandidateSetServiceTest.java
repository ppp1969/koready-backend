package koready_backend.onboarding.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.onboarding.application.port.CandidateSetRepository;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateItemRecord;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateSetRecord;
import koready_backend.onboarding.domain.CandidatePlaceReadiness;
import koready_backend.onboarding.domain.CandidateSetPolicyException;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@ExtendWith(MockitoExtension.class)
class CandidateSetServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
	private static final String SET_ID = "onb-test-set";

	@Mock
	private CandidateSetRepository repository;

	private CandidateSetService service;

	@BeforeEach
	void setUp() {
		service = new CandidateSetService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC),
			() -> SET_ID);
	}

	@Test
	void publishesTenReadyItemsAndAtomicallyReplacesCurrentSet() {
		CandidateSetRecord draft = set(CandidateSetStatus.DRAFT);
		List<CandidateItemRecord> items = items(10);
		when(repository.findByPublicIdForUpdate(SET_ID)).thenReturn(Optional.of(draft));
		when(repository.findItems(draft.id())).thenReturn(items);
		when(repository.findPlaceReadiness(any())).thenReturn(readiness(10));
		when(repository.markPublished(draft.id(), "42", NOW)).thenReturn(true);
		when(repository.findByPublicId(SET_ID)).thenReturn(Optional.of(
			publishedSet()));

		CandidateSetService.AdminCandidateSet result = service.publish(SET_ID, "42", true);

		assertEquals(CandidateSetStatus.PUBLISHED, result.status());
		verify(repository).replaceCurrent(draft.id(), NOW);
		verify(repository).recordAudit(any());
	}

	@Test
	void rejectsNineItemsBeforeChangingPublishedState() {
		CandidateSetRecord draft = set(CandidateSetStatus.DRAFT);
		when(repository.findByPublicIdForUpdate(SET_ID)).thenReturn(Optional.of(draft));
		when(repository.findItems(draft.id())).thenReturn(items(9));

		CandidateSetPolicyException exception = assertThrows(
			CandidateSetPolicyException.class,
			() -> service.publish(SET_ID, "42", true));

		assertEquals(CandidateSetPolicyException.Reason.REQUIRES_TEN_ITEMS,
			exception.reason());
		verify(repository, never()).markPublished(any(Long.class), any(), any());
		verify(repository, never()).replaceCurrent(any(Long.class), any());
	}

	private static CandidateSetRecord set(CandidateSetStatus status) {
		return new CandidateSetRecord(
			1L,
			SET_ID,
			"Summer curation",
			1,
			status,
			status == CandidateSetStatus.PUBLISHED ? NOW : null,
			status == CandidateSetStatus.PUBLISHED ? "42" : null,
			null,
			NOW,
			NOW,
			false,
			10);
	}

	private static CandidateSetRecord publishedSet() {
		return new CandidateSetRecord(
			1L,
			SET_ID,
			"Summer curation",
			1,
			CandidateSetStatus.PUBLISHED,
			NOW,
			"42",
			null,
			NOW,
			NOW,
			true,
			10);
	}

	private static List<CandidateItemRecord> items(int count) {
		return IntStream.rangeClosed(1, count)
			.mapToObj(index -> new CandidateItemRecord(
				index,
				index,
				null,
				"Message " + index,
				null,
				List.of("tag" + index),
				null,
				"Place " + index,
				null,
				"https://example.com/" + index + ".jpg",
				ServiceRegionCode.SEOUL,
				"Seoul",
				"Seoul",
				TravelStyle.LOCAL_FESTIVAL,
				CandidatePlaceReadiness.ready(index)))
			.toList();
	}

	private static Map<Long, CandidatePlaceReadiness> readiness(int count) {
		return IntStream.rangeClosed(1, count)
			.boxed()
			.collect(java.util.stream.Collectors.toMap(
				Integer::longValue,
				index -> CandidatePlaceReadiness.ready(index.longValue())));
	}
}
