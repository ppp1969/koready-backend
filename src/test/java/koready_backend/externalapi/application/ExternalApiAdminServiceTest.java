package koready_backend.externalapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.externalapi.application.exception.ExternalApiCallNotFoundException;
import koready_backend.externalapi.application.exception.InvalidExternalApiCursorException;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.CallCriteria;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.CallRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.ProviderAggregate;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SnapshotRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SyncCursorRecord;
import koready_backend.externalapi.application.port.ExternalApiAdminRepository.SummaryAggregate;
import koready_backend.externalapi.domain.ExternalApiProvider;
import koready_backend.externalapi.domain.RawSnapshotStatus;
import koready_backend.externalapi.domain.SnapshotRetentionClass;
import koready_backend.externalapi.domain.SnapshotStorageFormat;
import koready_backend.externalapi.domain.SyncCursorType;

@ExtendWith(MockitoExtension.class)
class ExternalApiAdminServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Mock
	ExternalApiAdminRepository repository;

	private ExternalApiAdminService service;

	@BeforeEach
	void setUp() {
		service = new ExternalApiAdminService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void defaultsSummaryToThirtyDaysAndCalculatesAStableSuccessRate() {
		CallRecord failure = call(2L, false, null);
		when(repository.summarize(any())).thenReturn(new SummaryAggregate(
			3,
			2,
			1,
			2,
			List.of(new ProviderAggregate(ExternalApiProvider.KTO, 3, 2, 1, NOW)),
			List.of(failure)));

		ExternalApiAdminService.OpenApiSummary result = service.summary(
			null, null, ExternalApiProvider.KTO);

		assertEquals(NOW.minus(Duration.ofDays(30)), result.period().from());
		assertEquals(NOW, result.period().to());
		assertEquals(66.7, result.successRate());
		assertEquals(1, result.recentFailures().size());
		ArgumentCaptor<ExternalApiAdminRepository.SummaryCriteria> captor =
			ArgumentCaptor.forClass(ExternalApiAdminRepository.SummaryCriteria.class);
		verify(repository).summarize(captor.capture());
		assertEquals(ExternalApiProvider.KTO, captor.getValue().provider());
	}

	@Test
	void bindsCallCursorToEveryFilter() {
		when(repository.findCallPage(any())).thenReturn(List.of(
			call(9L, true, snapshot(90L, null)),
			call(8L, true, snapshot(80L, null))));
		ExternalApiAdminService.CallQuery query = new ExternalApiAdminService.CallQuery(
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			true,
			200,
			NOW.minusSeconds(3600),
			NOW,
			12L,
			true,
			null,
			1);

		ExternalApiAdminService.CallPage first = service.listCalls(query);
		service.listCalls(new ExternalApiAdminService.CallQuery(
			query.provider(),
			query.apiName(),
			query.operation(),
			query.success(),
			query.httpStatus(),
			query.from(),
			query.to(),
			query.relatedJobId(),
			query.hasRawSnapshot(),
			first.nextCursor(),
			query.size()));

		ArgumentCaptor<CallCriteria> captor = ArgumentCaptor.forClass(CallCriteria.class);
		verify(repository, org.mockito.Mockito.times(2)).findCallPage(captor.capture());
		assertEquals(9L, captor.getAllValues().get(1).beforeId());
		assertTrue(first.hasMore());
		assertThrows(
			InvalidExternalApiCursorException.class,
			() -> service.listCalls(new ExternalApiAdminService.CallQuery(
				query.provider(),
				query.apiName(),
				"differentOperation",
				query.success(),
				query.httpStatus(),
				query.from(),
				query.to(),
				query.relatedJobId(),
				query.hasRawSnapshot(),
				first.nextCursor(),
				query.size())));
	}

	@Test
	void sanitizesCallDetailsAndNeverClaimsDownloadSupport() {
		SnapshotRecord snapshot = snapshot(77L, NOW.minusSeconds(1));
		when(repository.findCallById(7L)).thenReturn(Optional.of(call(7L, false, snapshot)));

		ExternalApiAdminService.CallView result = service.getCall(7L);

		assertEquals("https://apis.example.com/search", result.endpoint());
		assertEquals("***", result.requestParamsMasked().get("serviceKey"));
		assertFalse(result.requestParamsMasked().containsKey("query"));
		assertEquals("EXTERNAL_API_CALL_FAILED", result.error().code());
		assertEquals(RawSnapshotStatus.EXPIRED, result.rawSnapshotStatus());
		assertFalse(result.rawSnapshot().downloadable());
	}

	@Test
	void reportsMissingCalls() {
		when(repository.findCallById(404L)).thenReturn(Optional.empty());
		assertThrows(ExternalApiCallNotFoundException.class, () -> service.getCall(404L));
	}

	@Test
	void returnsActualSyncCursorStateIncludingNullableValues() {
		when(repository.findSyncCursors()).thenReturn(List.of(new SyncCursorRecord(
			13L,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			SyncCursorType.DATE_RANGE,
			null,
			NOW.minusSeconds(30),
			null,
			0,
			true,
			NOW.minusSeconds(60),
			NOW.minusSeconds(30))));

		ExternalApiAdminService.SyncCursorView result =
			service.listSyncCursors().getFirst();

		assertEquals(13L, result.cursorId());
		assertEquals("KOR", result.apiName());
		assertEquals(SyncCursorType.DATE_RANGE, result.cursorType());
		assertNull(result.cursorValue());
		assertNull(result.lastFailureAt());
		assertTrue(result.enabled());
	}

	private static CallRecord call(long id, boolean success, SnapshotRecord snapshot) {
		return new CallRecord(
			id,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			"https://apis.example.com/search?serviceKey=secret&query=dorm",
			NOW.minusSeconds(10),
			NOW.minusSeconds(9),
			1000L,
			success,
			success ? 200 : 503,
			Map.of("serviceKey", "secret", "query", "dorm", "pageNo", 1),
			Map.of("resultCode", success ? "0000" : "ERROR", "totalCount", 1),
			success ? "0000" : "ERROR",
			1,
			1024L,
			success ? null : "secret provider failure",
			12L,
			"KTO_DAILY_SYNC",
			snapshot);
	}

	private static SnapshotRecord snapshot(long id, Instant retentionUntil) {
		return new SnapshotRecord(
			id,
			7L,
			ExternalApiProvider.KTO,
			"KOR",
			"searchFestival2",
			"kto/kor/searchFestival2/snapshot.json.gz",
			SnapshotStorageFormat.JSON_GZIP,
			"application/json",
			"a".repeat(64),
			"b".repeat(64),
			1024,
			256,
			1,
			NOW.minusSeconds(9),
			SnapshotRetentionClass.COMPETITION_EVIDENCE,
			retentionUntil,
			true);
	}
}
