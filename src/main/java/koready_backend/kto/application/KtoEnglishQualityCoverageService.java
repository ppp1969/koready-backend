package koready_backend.kto.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.port.KtoEnglishQualityRepository;

@Service
public class KtoEnglishQualityCoverageService {

	private final KtoEnglishQualityRepository repository;
	private final Clock clock;

	@Autowired
	public KtoEnglishQualityCoverageService(
		KtoEnglishQualityRepository repository
	) {
		this(repository, Clock.systemUTC());
	}

	KtoEnglishQualityCoverageService(
		KtoEnglishQualityRepository repository,
		Clock clock
	) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Coverage coverage() {
		var value = repository.summarizeLatest();
		if (value.classified() + value.pending() != value.total()
			|| value.usable() + value.nonEnglishSuspected()
				+ value.encodingSuspected() + value.mixedOrUnknown()
				!= value.classified()) {
			throw new IllegalStateException(
				"KTO English quality coverage is inconsistent");
		}
		return new Coverage(
			Instant.now(clock),
			value.total(),
			value.classified(),
			value.pending(),
			rate(value.classified(), value.total()),
			value.usable(),
			value.nonEnglishSuspected(),
			value.encodingSuspected(),
			value.mixedOrUnknown());
	}

	private static BigDecimal rate(long numerator, long denominator) {
		if (denominator == 0) {
			return BigDecimal.ZERO.setScale(2);
		}
		return BigDecimal.valueOf(numerator)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
	}

	public record Coverage(
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
