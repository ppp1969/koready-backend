package koready_backend.evidence.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.evidence.application.port.EvidenceRawSnapshotReader;
import koready_backend.kto.infrastructure.config.KtoSnapshotProperties;

@Component
@ConditionalOnProperty(prefix = "koready.kto.snapshot", name = "storage", havingValue = "local", matchIfMissing = true)
public final class LocalEvidenceRawSnapshotReader implements EvidenceRawSnapshotReader {

	private final Path rootDirectory;

	public LocalEvidenceRawSnapshotReader(KtoSnapshotProperties properties) {
		this.rootDirectory = properties.directory().toAbsolutePath().normalize();
	}

	@Override
	public InputStream open(String storageKey) {
		if (storageKey == null || storageKey.isBlank()) {
			throw new IllegalArgumentException("Evidence raw snapshot key is invalid");
		}
		Path target = rootDirectory.resolve(storageKey).normalize();
		if (!target.startsWith(rootDirectory) || !Files.isRegularFile(target)) {
			throw new IllegalStateException("Evidence raw snapshot is unavailable");
		}
		try {
			return Files.newInputStream(target);
		} catch (IOException exception) {
			throw new IllegalStateException("Evidence raw snapshot is unavailable", exception);
		}
	}
}
