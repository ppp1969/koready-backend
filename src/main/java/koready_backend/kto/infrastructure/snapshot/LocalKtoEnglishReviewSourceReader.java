package koready_backend.kto.infrastructure.snapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.kto.application.port.KtoEnglishReviewSourceReader;
import koready_backend.kto.domain.KtoEnglishPlaceItem;
import koready_backend.kto.infrastructure.config.KtoSnapshotProperties;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.snapshot",
	name = "storage",
	havingValue = "local",
	matchIfMissing = true
)
final class LocalKtoEnglishReviewSourceReader implements KtoEnglishReviewSourceReader {

	private final Path rootDirectory;
	private final KtoEnglishReviewSnapshotParser parser;

	LocalKtoEnglishReviewSourceReader(
		KtoSnapshotProperties properties,
		KtoEnglishReviewSnapshotParser parser
	) {
		this.rootDirectory = properties.directory().toAbsolutePath().normalize();
		this.parser = parser;
	}

	@Override
	public Map<String, KtoEnglishPlaceItem> findAll(
		String storageKey,
		Collection<String> sourceContentIds
	) {
		Path target = safeTarget(storageKey);
		try {
			return parser.parse(Files.newInputStream(target), sourceContentIds);
		} catch (IOException exception) {
			throw new IllegalStateException("KTO English review snapshot is unavailable", exception);
		}
	}

	private Path safeTarget(String storageKey) {
		if (storageKey == null || storageKey.isBlank() || storageKey.length() > 1024) {
			throw new IllegalArgumentException("KTO English review snapshot key is invalid");
		}
		Path target = rootDirectory.resolve(storageKey).normalize();
		if (!target.startsWith(rootDirectory) || !Files.isRegularFile(target)) {
			throw new IllegalStateException("KTO English review snapshot is unavailable");
		}
		return target;
	}
}
