package koready_backend.kto.application.port;

import java.time.LocalDate;

public interface KtoRequestQuotaRepository {
	boolean reserve(LocalDate date, String operation, int limit);
}
