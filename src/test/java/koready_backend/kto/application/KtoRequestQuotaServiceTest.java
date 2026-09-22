package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import koready_backend.kto.application.port.KtoRequestQuotaRepository;
import koready_backend.kto.application.exception.KtoProviderException;

class KtoRequestQuotaServiceTest {
	@Test
	void reservesAgainstTheKoreanDateAndTheSpecificOperationLimit() {
		var repository = mock(KtoRequestQuotaRepository.class);
		var day = LocalDate.parse("2026-09-23");
		when(repository.reserve(day, "kor-detailcommon2", 5000)).thenReturn(true);
		var service = new KtoRequestQuotaService(repository,
			new KtoRequestQuotaProperties(1000, Map.of("kor-detailcommon2", 5000)),
			Clock.fixed(Instant.parse("2026-09-22T15:00:00Z"), ZoneOffset.UTC));
		service.reserve("kor-detailcommon2");
		verify(repository).reserve(day, "kor-detailcommon2", 5000);
		var error = assertThrows(KtoProviderException.class, () -> service.reserve("eng-areabasedsynclist2"));
		assertEquals("22", error.providerCode());
		verify(repository).reserve(day, "eng-areabasedsynclist2", 1000);
	}

	@Test
	void rejectsInvalidLimitsAndDoesNotSendAnInvalidOperationToStorage() {
		assertThrows(IllegalArgumentException.class, () -> new KtoRequestQuotaProperties(-1, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> new KtoRequestQuotaProperties(1000, Map.of("kor-test", -1)));
		var repository = mock(KtoRequestQuotaRepository.class);
		var service = new KtoRequestQuotaService(repository, new KtoRequestQuotaProperties(1000, null));
		assertThrows(IllegalArgumentException.class, () -> service.reserve("https://invalid?serviceKey=placeholder"));
		verifyNoInteractions(repository);
	}
}
