package koready_backend.kto.application.port;

import java.time.Instant;

public interface KtoCatalogReconciliationStore {
	int deactivatePlacesNotSeenSince(Instant catalogRunStartedAt);
}
