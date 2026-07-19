package koready_backend.kto.infrastructure.snapshot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.kto.application.port.KtoRawSnapshotDownloadUrlProvider;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.snapshot",
	name = "storage",
	havingValue = "local",
	matchIfMissing = true)
public final class UnavailableKtoRawSnapshotDownloadUrlProvider
	implements KtoRawSnapshotDownloadUrlProvider {

	@Override
	public boolean available() {
		return false;
	}

	@Override
	public DownloadLink issue(String storageKey, String fileName) {
		throw new IllegalStateException("KTO snapshot download is unavailable");
	}
}
