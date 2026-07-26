package koready_backend.kto.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.port.KtoDetailCoverageRepository;

@Service
public class KtoDetailCoverageService {

	private final KtoDetailCoverageRepository repository;
	private final Clock clock;

	@Autowired
	public KtoDetailCoverageService(KtoDetailCoverageRepository repository) {
		this(repository, Clock.systemUTC());
	}

	KtoDetailCoverageService(
		KtoDetailCoverageRepository repository,
		Clock clock
	) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CoverageSummary summary() {
		var aggregate = repository.summarize();
		long pendingPlaces = aggregate.totalPlaces() - aggregate.completedPlaces();
		if (pendingPlaces < 0
			|| aggregate.ktoDetailImages().total() != aggregate.completedPlaces()
			|| aggregate.effectiveGalleryImages().total()
				!= aggregate.completedPlaces()) {
			throw new IllegalStateException(
				"KTO detail coverage aggregate is inconsistent");
		}
		return new CoverageSummary(
			clock.instant(),
			new CatalogCoverage(
				aggregate.totalPlaces(),
				aggregate.completedPlaces(),
				pendingPlaces,
				aggregate.dueForRefreshPlaces(),
				percentage(aggregate.completedPlaces(), aggregate.totalPlaces())),
			images(aggregate.ktoDetailImages()),
			images(aggregate.effectiveGalleryImages()));
	}

	private ImageCoverage images(
		KtoDetailCoverageRepository.ImageBuckets buckets
	) {
		long lessThanFour = buckets.lessThanFour();
		return new ImageCoverage(
			buckets.total(),
			buckets.zero(),
			buckets.one(),
			buckets.two(),
			buckets.three(),
			buckets.fourOrMore(),
			lessThanFour,
			percentage(lessThanFour, buckets.total()));
	}

	private double percentage(long value, long total) {
		if (total == 0) {
			return 0.0;
		}
		return BigDecimal.valueOf(value)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
			.doubleValue();
	}

	public record CoverageSummary(
		Instant generatedAt,
		CatalogCoverage catalog,
		ImageCoverage ktoDetailImages,
		ImageCoverage effectiveGalleryImages
	) {
	}

	public record CatalogCoverage(
		long totalPlaces,
		long completedPlaces,
		long pendingPlaces,
		long dueForRefreshPlaces,
		double completionRate
	) {
	}

	public record ImageCoverage(
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
