package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.port.KtoEnglishQualityRepository;
import koready_backend.kto.application.port.KtoEnglishQualityRepository.QualityCoverage;

@ExtendWith(MockitoExtension.class)
class KtoEnglishQualityCoverageServiceTest {

	@Mock
	KtoEnglishQualityRepository repository;

	@Test
	void reportsPartialBackfillAndQualityBuckets() {
		when(repository.summarizeLatest()).thenReturn(
			new QualityCoverage(100, 40, 60, 30, 5, 2, 3));
		var service = service();

		var result = service.coverage();

		assertEquals(new BigDecimal("40.00"), result.completionRate());
		assertEquals(60, result.pending());
		assertEquals(30, result.usable());
	}

	@Test
	void rejectsAnInconsistentAggregate() {
		when(repository.summarizeLatest()).thenReturn(
			new QualityCoverage(100, 40, 60, 40, 5, 2, 3));

		assertThrows(IllegalStateException.class, () -> service().coverage());
	}

	private KtoEnglishQualityCoverageService service() {
		return new KtoEnglishQualityCoverageService(
			repository,
			Clock.fixed(
				Instant.parse("2026-07-27T00:00:00Z"),
				ZoneOffset.UTC));
	}
}
