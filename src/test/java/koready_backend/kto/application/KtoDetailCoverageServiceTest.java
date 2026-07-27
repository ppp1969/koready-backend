package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.kto.application.port.KtoDetailCoverageRepository;

@ExtendWith(MockitoExtension.class)
class KtoDetailCoverageServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

	@Mock
	KtoDetailCoverageRepository repository;

	@Test
	void calculatesCatalogAndLessThanFourRates() {
		var ktoImages = new KtoDetailCoverageRepository.ImageBuckets(
			2, 1, 1, 1, 1);
		var galleryImages = new KtoDetailCoverageRepository.ImageBuckets(
			1, 2, 1, 1, 1);
		when(repository.summarize()).thenReturn(
			new KtoDetailCoverageRepository.CoverageAggregate(
				7, 6, 1, ktoImages, galleryImages));
		var service = new KtoDetailCoverageService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.summary();

		assertEquals(NOW, result.generatedAt());
		assertEquals(7, result.catalog().totalPlaces());
		assertEquals(6, result.catalog().completedPlaces());
		assertEquals(1, result.catalog().pendingPlaces());
		assertEquals(1, result.catalog().dueForRefreshPlaces());
		assertEquals(85.71, result.catalog().completionRate());
		assertEquals(5, result.ktoDetailImages().lessThanFour());
		assertEquals(83.33, result.ktoDetailImages().lessThanFourRate());
		assertEquals(5, result.effectiveGalleryImages().lessThanFour());
		assertEquals(83.33, result.effectiveGalleryImages().lessThanFourRate());
	}

	@Test
	void returnsZeroRatesForAnEmptyCatalog() {
		var empty = new KtoDetailCoverageRepository.ImageBuckets(0, 0, 0, 0, 0);
		when(repository.summarize()).thenReturn(
			new KtoDetailCoverageRepository.CoverageAggregate(
				0, 0, 0, empty, empty));
		var service = new KtoDetailCoverageService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));

		var result = service.summary();

		assertEquals(0.0, result.catalog().completionRate());
		assertEquals(0.0, result.ktoDetailImages().lessThanFourRate());
		assertEquals(0.0, result.effectiveGalleryImages().lessThanFourRate());
	}
}
