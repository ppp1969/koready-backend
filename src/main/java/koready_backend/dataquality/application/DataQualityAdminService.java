package koready_backend.dataquality.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.dataquality.application.port.DataQualityRepository;

@Service
public class DataQualityAdminService {

	private final DataQualityRepository repository;
	private final Clock clock;

	@Autowired
	public DataQualityAdminService(DataQualityRepository repository) {
		this(repository, Clock.systemUTC());
	}

	DataQualityAdminService(DataQualityRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DataQualitySummary summary() {
		var aggregate = repository.summarize();
		return new DataQualitySummary(
			clock.instant(),
			new PlaceQualitySummary(
				aggregate.places().total(),
				aggregate.places().active(),
				aggregate.places().missingImage(),
				aggregate.places().missingEnglish(),
				aggregate.places().missingCoordinates(),
				aggregate.places().missingAddress(),
				aggregate.places().curationReady()),
			new LocalizationQualitySummary(
				aggregate.localization().ktoEnglish(),
				aggregate.localization().aiTranslated(),
				aggregate.localization().manualEdited()),
			aggregate.lastSuccessfulSyncAt());
	}

	public record DataQualitySummary(
		Instant generatedAt,
		PlaceQualitySummary places,
		LocalizationQualitySummary localization,
		Instant lastSuccessfulSyncAt
	) {
	}

	public record PlaceQualitySummary(
		long total,
		long active,
		long missingImage,
		long missingEnglish,
		long missingCoordinates,
		long missingAddress,
		long curationReady
	) {
	}

	public record LocalizationQualitySummary(
		long ktoEnglish,
		long aiTranslated,
		long manualEdited
	) {
	}
}
