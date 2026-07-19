package koready_backend.dataquality.controller;

import java.time.Instant;

import koready_backend.dataquality.application.DataQualityAdminService;

final class DataQualityDtos {

	private DataQualityDtos() {
	}

	static DataQualityResponse from(DataQualityAdminService.DataQualitySummary summary) {
		return new DataQualityResponse(
			summary.generatedAt(),
			new PlaceQualityResponse(
				summary.places().total(),
				summary.places().active(),
				summary.places().missingImage(),
				summary.places().missingEnglish(),
				summary.places().missingCoordinates(),
				summary.places().missingAddress(),
				summary.places().curationReady()),
			new LocalizationQualityResponse(
				summary.localization().ktoEnglish(),
				summary.localization().aiTranslated(),
				summary.localization().manualEdited()),
			summary.lastSuccessfulSyncAt());
	}

	record DataQualityResponse(
		Instant generatedAt,
		PlaceQualityResponse places,
		LocalizationQualityResponse localization,
		Instant lastSuccessfulSyncAt
	) {
	}

	record PlaceQualityResponse(
		long total,
		long active,
		long missingImage,
		long missingEnglish,
		long missingCoordinates,
		long missingAddress,
		long curationReady
	) {
	}

	record LocalizationQualityResponse(
		long ktoEnglish,
		long aiTranslated,
		long manualEdited
	) {
	}
}
