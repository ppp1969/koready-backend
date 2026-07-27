package koready_backend.kto.controller;

import java.math.BigDecimal;
import java.time.Instant;

import koready_backend.kto.application.KtoEnglishQualityCoverageService;

final class KtoEnglishQualityCoverageDtos {

	private KtoEnglishQualityCoverageDtos() {
	}

	static CoverageResponse from(
		KtoEnglishQualityCoverageService.Coverage coverage
	) {
		return new CoverageResponse(
			coverage.generatedAt(),
			coverage.total(),
			coverage.classified(),
			coverage.pending(),
			coverage.completionRate(),
			coverage.usable(),
			coverage.nonEnglishSuspected(),
			coverage.encodingSuspected(),
			coverage.mixedOrUnknown());
	}

	record CoverageResponse(
		Instant generatedAt,
		long total,
		long classified,
		long pending,
		BigDecimal completionRate,
		long usable,
		long nonEnglishSuspected,
		long encodingSuspected,
		long mixedOrUnknown
	) {
	}
}
