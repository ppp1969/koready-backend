package koready_backend.horitip.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.horitip.application.exception.HoriTipCodeDuplicatedException;
import koready_backend.horitip.application.exception.HoriTipConcurrentModificationException;
import koready_backend.horitip.application.exception.InvalidHoriTipCursorException;
import koready_backend.horitip.application.port.HoriTipRepository;
import koready_backend.horitip.application.port.HoriTipRepository.AuditRecord;
import koready_backend.horitip.application.port.HoriTipRepository.HoriTipRecord;
import koready_backend.horitip.domain.HoriTipDraft;
import koready_backend.horitip.domain.HoriTipPlacement;
import koready_backend.horitip.domain.HoriTipPolicyException;
import koready_backend.horitip.domain.HoriTipRouteMode;
import koready_backend.horitip.domain.HoriTipScope;
import koready_backend.horitip.domain.HoriTipScopeType;
import koready_backend.horitip.domain.HoriTipStatus;
import koready_backend.horitip.domain.HoriTipStatusTarget;
import koready_backend.horitip.domain.HoriTipTranslation;
import koready_backend.horitip.domain.HoriTipTrigger;
import koready_backend.place.domain.PlaceLanguage;

@ExtendWith(MockitoExtension.class)
class HoriTipServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T08:00:00Z");
	private static final String ACTOR = "admin_subject";

	@Mock
	HoriTipRepository repository;

	private HoriTipService service;

	@BeforeEach
	void setUp() {
		service = new HoriTipService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsAnOperatorDraftAndRecordsAnAuditSnapshot() {
		HoriTipDraft draft = destinationDraft(false);
		when(repository.findVisiblePlaceIds(List.of(10L))).thenReturn(Set.of(10L));
		when(repository.insertDraft(any())).thenReturn(Optional.of(record(
			7L, HoriTipStatus.DRAFT, draft, 1, null, null)));

		HoriTipService.HoriTipView result = service.create(
			new HoriTipService.CreateCommand("TIP_STATION", draft),
			ACTOR,
			true);

		assertEquals(7L, result.horiTipId());
		assertEquals("OPERATOR_CURATED", result.source());
		assertEquals(HoriTipStatus.DRAFT, result.status());
		assertFalse(result.activeNow());
		assertTrue(result.editable());
		ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("HORI_TIP_CREATED", auditCaptor.getValue().action());
		assertEquals(ACTOR, auditCaptor.getValue().actorSubject());
		assertEquals(1, auditCaptor.getValue().after().version());
	}

	@Test
	void rejectsAConcurrentCodeCollisionWithoutWritingAnAudit() {
		HoriTipDraft draft = destinationDraft(false);
		when(repository.findVisiblePlaceIds(List.of(10L))).thenReturn(Set.of(10L));
		when(repository.insertDraft(any())).thenReturn(Optional.empty());

		assertThrows(
			HoriTipCodeDuplicatedException.class,
			() -> service.create(
				new HoriTipService.CreateCommand("TIP_STATION", draft),
				ACTOR,
				true));
		verify(repository, never()).recordAudit(any());
	}

	@Test
	void activatesACompleteTipAndCalculatesActiveNow() {
		HoriTipDraft draft = destinationDraft(true);
		HoriTipRecord existing = record(7L, HoriTipStatus.DRAFT, draft, 1, null, null);
		HoriTipRecord active = record(7L, HoriTipStatus.ACTIVE, draft, 2, NOW, null);
		when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(existing));
		when(repository.findVisiblePlaceIds(List.of(10L))).thenReturn(Set.of(10L));
		when(repository.updateStatus(7L, HoriTipStatus.ACTIVE, ACTOR, NOW))
			.thenReturn(active);

		HoriTipService.HoriTipView result = service.changeStatus(
			7L,
			new HoriTipService.StatusCommand(
				HoriTipStatusTarget.ACTIVE,
				1,
				"Reviewed"),
			ACTOR,
			true);

		assertEquals(HoriTipStatus.ACTIVE, result.status());
		assertEquals(2, result.version());
		assertTrue(result.activeNow());
		ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("Reviewed", auditCaptor.getValue().reason());
		assertEquals(HoriTipStatus.DRAFT, auditCaptor.getValue().before().status());
		assertEquals(HoriTipStatus.ACTIVE, auditCaptor.getValue().after().status());
	}

	@Test
	void rejectsAStaleVersionBeforeChangingStoredData() {
		HoriTipRecord existing = record(
			7L,
			HoriTipStatus.INACTIVE,
			destinationDraft(true),
			3,
			NOW.minusSeconds(60),
			null);
		when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(existing));

		assertThrows(
			HoriTipConcurrentModificationException.class,
			() -> service.update(
				7L,
				new HoriTipService.UpdateCommand(2, destinationDraft(true)),
				ACTOR,
				true));
		verify(repository, never()).updateDraft(anyLong(), any(), any(), any());
	}

	@Test
	void updatesAnEditableTipAndRecordsBothSnapshots() {
		HoriTipDraft beforeDraft = destinationDraft(false);
		HoriTipDraft afterDraft = destinationDraft(true);
		HoriTipRecord before = record(7L, HoriTipStatus.DRAFT, beforeDraft, 1, null, null);
		HoriTipRecord after = record(7L, HoriTipStatus.DRAFT, afterDraft, 2, null, null);
		when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(before));
		when(repository.findVisiblePlaceIds(List.of(10L))).thenReturn(Set.of(10L));
		when(repository.updateDraft(7L, afterDraft, ACTOR, NOW)).thenReturn(after);

		HoriTipService.HoriTipView result = service.update(
			7L,
			new HoriTipService.UpdateCommand(1, afterDraft),
			ACTOR,
			true);

		assertEquals(2, result.version());
		ArgumentCaptor<AuditRecord> auditCaptor = ArgumentCaptor.forClass(AuditRecord.class);
		verify(repository).recordAudit(auditCaptor.capture());
		assertEquals("HORI_TIP_UPDATED", auditCaptor.getValue().action());
		assertEquals(1, auditCaptor.getValue().before().version());
		assertEquals(2, auditCaptor.getValue().after().version());
	}

	@Test
	void rejectsInvisibleDestinationsAndArchivedChanges() {
		HoriTipDraft draft = destinationDraft(false);
		when(repository.findVisiblePlaceIds(List.of(10L))).thenReturn(Set.of());

		HoriTipPolicyException invalidDestination = assertThrows(
			HoriTipPolicyException.class,
			() -> service.create(
				new HoriTipService.CreateCommand("TIP_STATION", draft), ACTOR, true));
		assertEquals(HoriTipPolicyException.Reason.RULE_INVALID, invalidDestination.reason());

		HoriTipRecord archived = record(
			7L, HoriTipStatus.ARCHIVED, draft, 2, NOW.minusSeconds(60), NOW);
		when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(archived));
		HoriTipPolicyException notEditable = assertThrows(
			HoriTipPolicyException.class,
			() -> service.changeStatus(
				7L,
				new HoriTipService.StatusCommand(HoriTipStatusTarget.ACTIVE, 2, "Retry"),
				ACTOR,
				true));
		assertEquals(HoriTipPolicyException.Reason.NOT_EDITABLE, notEditable.reason());
	}

	@Test
	void bindsTheCursorToEveryListFilter() {
		HoriTipRecord first = record(
			9L, HoriTipStatus.DRAFT, destinationDraft(false), 1, null, null);
		HoriTipRecord second = record(
			8L, HoriTipStatus.DRAFT, destinationDraft(false), 1, null, null);
		when(repository.findPage(any())).thenReturn(List.of(first, second));

		HoriTipService.HoriTipPage firstPage = service.list(
			HoriTipStatus.DRAFT,
			"TIP_",
			10L,
			null,
			1,
			true);
		service.list(
			HoriTipStatus.DRAFT,
			"TIP_",
			10L,
			firstPage.nextCursor(),
			1,
			true);

		ArgumentCaptor<HoriTipRepository.ListCriteria> criteriaCaptor =
			ArgumentCaptor.forClass(HoriTipRepository.ListCriteria.class);
		verify(repository, org.mockito.Mockito.times(2)).findPage(criteriaCaptor.capture());
		assertEquals(9L, criteriaCaptor.getAllValues().get(1).beforeId());
		assertTrue(firstPage.hasMore());
		assertThrows(
			InvalidHoriTipCursorException.class,
			() -> service.list(
				HoriTipStatus.DRAFT,
				"DIFFERENT",
				10L,
				firstPage.nextCursor(),
				1,
				true));
	}

	private static HoriTipDraft destinationDraft(boolean completeTranslations) {
		List<HoriTipTranslation> translations = completeTranslations
			? List.of(
				new HoriTipTranslation(PlaceLanguage.KO, "Korean body"),
				new HoriTipTranslation(PlaceLanguage.EN, "English body"))
			: List.of(new HoriTipTranslation(PlaceLanguage.KO, "Korean body"));
		return new HoriTipDraft(
			HoriTipPlacement.AFTER_SEGMENT,
			100,
			new HoriTipScope(HoriTipScopeType.DESTINATION_PLACES, List.of(10L)),
			new HoriTipTrigger(
				List.of(HoriTipRouteMode.TRAIN),
				List.of("KTX"),
				List.of(),
				List.of("Gimcheon"),
				3600,
				null,
				null),
			translations,
			NOW.minusSeconds(60),
			NOW.plusSeconds(3600),
			"Operator note");
	}

	private static HoriTipRecord record(
		long id,
		HoriTipStatus status,
		HoriTipDraft draft,
		int version,
		Instant activatedAt,
		Instant archivedAt
	) {
		return new HoriTipRecord(
			id,
			"TIP_STATION",
			status,
			draft,
			version,
			ACTOR,
			ACTOR,
			activatedAt,
			archivedAt,
			NOW.minusSeconds(120),
			NOW);
	}
}
