package koready_backend.onboarding.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.onboarding.application.port.CandidateSetRepository;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateItemRecord;
import koready_backend.onboarding.application.port.CandidateSetRepository.CandidateSetRecord;
import koready_backend.onboarding.domain.CandidatePlaceReadiness;
import koready_backend.onboarding.domain.CandidateSetDraft;
import koready_backend.onboarding.domain.CandidateSetStatus;
import koready_backend.onboarding.domain.InitialCandidatePlace;
import koready_backend.onboarding.domain.InitialCandidatePlaceCatalog;

@ExtendWith(MockitoExtension.class)
class InitialCandidateSetBootstrapServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-18T13:00:00Z");
	private static final String SET_ID = "onb-kto-curated-v1";

	@Mock
	private CandidateSetRepository repository;

	private InitialCandidateSetBootstrapService service;

	@BeforeEach
	void setUp() {
		service = new InitialCandidateSetBootstrapService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsAndPublishesTheDeterministicInitialSet() {
		Map<String, Long> placeIds = placeIds();
		CandidateSetRecord draft = record(CandidateSetStatus.DRAFT, false);
		when(repository.findByPublicIdForUpdate(SET_ID)).thenReturn(Optional.empty());
		when(repository.insertDraft(any(), any(), any())).thenReturn(draft);
		when(repository.findPlaceReadiness(any())).thenAnswer(invocation ->
			invocation.<List<koready_backend.onboarding.domain.CandidateSetItemDraft>>getArgument(0)
				.stream().collect(java.util.stream.Collectors.toMap(
					item -> item.placeId(),
					item -> CandidatePlaceReadiness.ready(item.placeId()))));
		when(repository.markPublished(draft.id(), "SYSTEM:CURATED_KTO_BOOTSTRAP", NOW))
			.thenReturn(true);

		InitialCandidateSetBootstrapResult result = service.bootstrap(placeIds);

		assertEquals(SET_ID, result.candidateSetId());
		assertEquals(false, result.replayed());
		ArgumentCaptor<CandidateSetDraft> draftCaptor = ArgumentCaptor.forClass(CandidateSetDraft.class);
		verify(repository).replaceDraft(eq(draft.id()), draftCaptor.capture(), eq(NOW));
		assertEquals(10, draftCaptor.getValue().items().size());
		assertEquals(1, draftCaptor.getValue().items().getFirst().displayOrder());
		assertEquals(placeIds.get("126508"), draftCaptor.getValue().items().getFirst().placeId());
		verify(repository).replaceCurrent(draft.id(), NOW);
		verify(repository, times(2)).recordAudit(any());
	}

	@Test
	void replaysAnIdenticalPublishedSetWithoutMutatingItsItems() {
		Map<String, Long> placeIds = placeIds();
		CandidateSetRecord published = record(CandidateSetStatus.PUBLISHED, false);
		when(repository.findByPublicIdForUpdate(SET_ID)).thenReturn(Optional.of(published));
		when(repository.findItems(published.id())).thenReturn(items(placeIds));

		InitialCandidateSetBootstrapResult result = service.bootstrap(placeIds);

		assertEquals(true, result.replayed());
		verify(repository, never()).replaceDraft(any(Long.class), any(), any());
		verify(repository, never()).markPublished(any(Long.class), any(), any());
		verify(repository).replaceCurrent(published.id(), NOW);
		verify(repository).recordAudit(any());
	}

	@Test
	void rejectsAChangedPublishedSetInsteadOfSilentlyOverwritingIt() {
		Map<String, Long> placeIds = placeIds();
		CandidateSetRecord published = record(CandidateSetStatus.PUBLISHED, true);
		when(repository.findByPublicIdForUpdate(SET_ID)).thenReturn(Optional.of(published));
		List<CandidateItemRecord> changed = new java.util.ArrayList<>(items(placeIds));
		CandidateItemRecord first = changed.getFirst();
		changed.set(0, new CandidateItemRecord(
			first.placeId(), first.displayOrder(), first.representativeImageId(),
			"Changed", first.curatorMessageEn(), first.displayTags(), first.editorNote(),
			first.titleKo(), first.titleEn(), first.imageUrl(), first.serviceRegionCode(),
			first.serviceRegionNameKo(), first.serviceRegionNameEn(), first.travelStyle(),
			first.readiness()));
		when(repository.findItems(published.id())).thenReturn(changed);

		assertThrows(IllegalStateException.class, () -> service.bootstrap(placeIds));
		verify(repository, never()).replaceCurrent(any(Long.class), any());
	}

	private static CandidateSetRecord record(CandidateSetStatus status, boolean current) {
		return new CandidateSetRecord(
			41L, SET_ID, "KoReady 온보딩 대표 관광지 v1", 41, status,
			status == CandidateSetStatus.PUBLISHED ? NOW : null,
			status == CandidateSetStatus.PUBLISHED ? "SYSTEM:CURATED_KTO_BOOTSTRAP" : null,
			null, NOW, NOW, current, 10);
	}

	private static Map<String, Long> placeIds() {
		Map<String, Long> result = new LinkedHashMap<>();
		for (InitialCandidatePlace place : InitialCandidatePlaceCatalog.approved()) {
			result.put(place.ktoContentId(), 100L + place.displayOrder());
		}
		return Map.copyOf(result);
	}

	private static List<CandidateItemRecord> items(Map<String, Long> placeIds) {
		return InitialCandidatePlaceCatalog.approved().stream()
			.map(place -> new CandidateItemRecord(
				placeIds.get(place.ktoContentId()),
				place.displayOrder(),
				null,
				place.curatorMessageKo(),
				place.curatorMessageEn(),
				place.displayTags(),
				place.editorNote(),
				place.expectedKtoTitleKo(),
				place.titleEn(),
				"https://example.invalid/image.jpg",
				place.serviceRegionCode(),
				place.serviceRegionCode().name(),
				place.serviceRegionCode().name(),
				place.travelStyle(),
				CandidatePlaceReadiness.ready(placeIds.get(place.ktoContentId()))))
			.toList();
	}
}
