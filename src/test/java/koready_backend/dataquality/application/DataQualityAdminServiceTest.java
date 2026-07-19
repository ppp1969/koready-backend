package koready_backend.dataquality.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.dataquality.application.port.DataQualityRepository;
import koready_backend.dataquality.application.port.DataQualityRepository.DataQualityAggregate;
import koready_backend.dataquality.application.port.DataQualityRepository.LocalizationAggregate;
import koready_backend.dataquality.application.port.DataQualityRepository.PlaceAggregate;

@ExtendWith(MockitoExtension.class)
class DataQualityAdminServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
	private static final Instant LAST_SYNC = Instant.parse("2026-07-19T08:30:00Z");

	@Mock
	DataQualityRepository repository;

	@Test
	void returnsTheStoredAggregatesWithAStableGenerationTime() {
		when(repository.summarize()).thenReturn(new DataQualityAggregate(
			new PlaceAggregate(4, 3, 1, 1, 1, 1, 2),
			new LocalizationAggregate(1, 1, 1),
			LAST_SYNC));
		DataQualityAdminService service = new DataQualityAdminService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));

		DataQualityAdminService.DataQualitySummary result = service.summary();

		assertEquals(NOW, result.generatedAt());
		assertEquals(4, result.places().total());
		assertEquals(3, result.places().active());
		assertEquals(1, result.places().missingImage());
		assertEquals(1, result.places().missingEnglish());
		assertEquals(1, result.places().missingCoordinates());
		assertEquals(1, result.places().missingAddress());
		assertEquals(2, result.places().curationReady());
		assertEquals(1, result.localization().ktoEnglish());
		assertEquals(1, result.localization().aiTranslated());
		assertEquals(1, result.localization().manualEdited());
		assertEquals(LAST_SYNC, result.lastSuccessfulSyncAt());
		verify(repository).summarize();
	}
}
