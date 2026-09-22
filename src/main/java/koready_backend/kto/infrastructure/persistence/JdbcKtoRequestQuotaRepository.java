package koready_backend.kto.infrastructure.persistence;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import koready_backend.kto.application.port.KtoRequestQuotaRepository;

@Repository
public class JdbcKtoRequestQuotaRepository implements KtoRequestQuotaRepository {
	private final JdbcTemplate jdbc;
	public JdbcKtoRequestQuotaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean reserve(LocalDate date, String operation, int limit) {
		if (limit <= 0) { return false; }
		jdbc.update("""
			INSERT INTO kto_daily_request_usage (usage_date, operation_key, reserved_requests)
			VALUES (?, ?, 0)
			ON DUPLICATE KEY UPDATE operation_key = VALUES(operation_key)
			""", date, operation);
		return jdbc.update("""
			UPDATE kto_daily_request_usage SET reserved_requests = reserved_requests + 1
			WHERE usage_date = ? AND operation_key = ? AND reserved_requests < ? AND blocked = FALSE
			""", date, operation, limit) == 1;
	}
}
