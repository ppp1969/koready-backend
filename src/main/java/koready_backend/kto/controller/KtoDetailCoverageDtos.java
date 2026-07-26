package koready_backend.kto.controller;

import java.time.Instant;

import koready_backend.kto.application.KtoDetailCoverageService;

final class KtoDetailCoverageDtos {

	private KtoDetailCoverageDtos() {
	}

	static CoverageResponse from(
		KtoDetailCoverageService.CoverageSummary summary
	) {
		return new CoverageResponse(
			summary.generatedAt(),
			new CatalogCoverageResponse(
				summary.catalog().totalPlaces(),
				summary.catalog().completedPlaces(),
				summary.catalog().pendingPlaces(),
				summary.catalog().dueForRefreshPlaces(),
				summary.catalog().completionRate()),
			images(summary.ktoDetailImages()),
			images(summary.effectiveGalleryImages()));
	}

	private static ImageCoverageResponse images(
		KtoDetailCoverageService.ImageCoverage coverage
	) {
		return new ImageCoverageResponse(
			coverage.sampleSize(),
			coverage.zero(),
			coverage.one(),
			coverage.two(),
			coverage.three(),
			coverage.fourOrMore(),
			coverage.lessThanFour(),
			coverage.lessThanFourRate());
	}

	record CoverageResponse(
		Instant generatedAt,
		CatalogCoverageResponse catalog,
		ImageCoverageResponse ktoDetailImages,
		ImageCoverageResponse effectiveGalleryImages
	) {
	}

	record CatalogCoverageResponse(
		long totalPlaces,
		long completedPlaces,
		long pendingPlaces,
		long dueForRefreshPlaces,
		double completionRate
	) {
	}

	record ImageCoverageResponse(
		long sampleSize,
		long zero,
		long one,
		long two,
		long three,
		long fourOrMore,
		long lessThanFour,
		double lessThanFourRate
	) {
	}
}
