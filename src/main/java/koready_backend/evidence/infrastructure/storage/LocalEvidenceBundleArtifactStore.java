package koready_backend.evidence.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.evidence.application.port.EvidenceBundleArtifactStore;
import koready_backend.evidence.infrastructure.config.EvidenceBundleProperties;

@Component
@ConditionalOnProperty(prefix = "koready.kto.snapshot", name = "storage", havingValue = "local", matchIfMissing = true)
public final class LocalEvidenceBundleArtifactStore implements EvidenceBundleArtifactStore {

	private static final Pattern BUNDLE_ID = Pattern.compile("evidence_[A-Za-z0-9]{8,64}");
	private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,200}");

	private final Path rootDirectory;

	public LocalEvidenceBundleArtifactStore(EvidenceBundleProperties properties) {
		this.rootDirectory = properties.directory().toAbsolutePath().normalize();
	}

	@Override
	public StoredArtifact store(String bundleId, String fileName, Path source, String sha256, long byteSize) {
		if (!BUNDLE_ID.matcher(bundleId).matches() || !FILE_NAME.matcher(fileName).matches()
			|| source == null || !Files.isRegularFile(source) || sha256 == null || !sha256.matches("[a-f0-9]{64}")
			|| byteSize < 0) {
			throw new IllegalArgumentException("Evidence bundle artifact is invalid");
		}
		Path target = rootDirectory.resolve(bundleId).resolve(fileName).normalize();
		if (!target.startsWith(rootDirectory)) {
			throw new IllegalArgumentException("Evidence bundle artifact is invalid");
		}
		try {
			Files.createDirectories(target.getParent());
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			return new StoredArtifact(rootDirectory.relativize(target).toString().replace('\\', '/'));
		} catch (IOException exception) {
			throw new IllegalStateException("Evidence bundle artifact could not be stored", exception);
		}
	}

	@Override
	public DownloadLink createDownloadUrl(String storageKey, String fileName) {
		throw new IllegalStateException("Evidence bundle signed download requires S3 storage");
	}
}
