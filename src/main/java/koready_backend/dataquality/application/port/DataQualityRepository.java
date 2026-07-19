package koready_backend.dataquality.application.port;

import java.time.Instant;

public interface DataQualityRepository {

	DataQualityAggregate summarize();

	record DataQualityAggregate(
		PlaceAggregate places,
		LocalizationAggregate localization,
		Instant lastSuccessfulSyncAt
	) {
	}

	record PlaceAggregate(
		long total,
		long active,
		long missingImage,
		long missingEnglish,
		long missingCoordinates,
		long missingAddress,
		long curationReady
	) {
	}

	record LocalizationAggregate(
		long ktoEnglish,
		long aiTranslated,
		long manualEdited
	) {
	}
}
