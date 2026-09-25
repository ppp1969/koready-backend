package koready_backend.kto.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import koready_backend.kto.application.port.KtoRequestQuotaRepository;
import koready_backend.kto.application.exception.KtoProviderException;

@Service
public class KtoRequestQuotaService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final KtoRequestQuotaRepository repository;
	private final KtoRequestQuotaProperties properties;
	private final Clock clock;

	@Autowired
	public KtoRequestQuotaService(KtoRequestQuotaRepository repository, KtoRequestQuotaProperties properties) {
		this(repository, properties, Clock.systemUTC());
	}
	KtoRequestQuotaService(KtoRequestQuotaRepository repository, KtoRequestQuotaProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}
	public void reserve(String operation) {
		if (operation == null || !operation.matches("[a-z0-9-]{3,100}")) {
			throw new IllegalArgumentException("Invalid KTO quota operation");
		}
		if (!repository.reserve(LocalDate.now(clock.withZone(SEOUL)), operation, properties.limit(operation))) {
			throw new KtoProviderException("22");
		}
	}
}
