package koready_backend.kto.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import koready_backend.kto.application.port.KtoCatalogReconciliationStore;

@Repository
public class JdbcKtoCatalogReconciliationStore
	implements KtoCatalogReconciliationStore {

	private final JdbcTemplate jdbcTemplate;

	public JdbcKtoCatalogReconciliationStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public int deactivatePlacesNotSeenSince(Instant catalogRunStartedAt) {
		if (catalogRunStartedAt == null) {
			throw new IllegalArgumentException("Catalog run start time is required");
		}
		return jdbcTemplate.update("""
			UPDATE places
			SET active = FALSE
			WHERE kto_content_id IS NOT NULL
			  AND active = TRUE
			  AND (kto_catalog_seen_at IS NULL OR kto_catalog_seen_at < ?)
			""", Timestamp.from(catalogRunStartedAt));
	}
}
