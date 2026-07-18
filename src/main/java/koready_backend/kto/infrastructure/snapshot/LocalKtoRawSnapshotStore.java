package koready_backend.kto.infrastructure.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import koready_backend.kto.application.exception.KtoSnapshotConflictException;
import koready_backend.kto.application.exception.KtoSnapshotStorageException;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.infrastructure.config.KtoSnapshotProperties;

@Component
@ConditionalOnProperty(
	prefix = "koready.kto.snapshot",
	name = "storage",
	havingValue = "local",
	matchIfMissing = true)
public final class LocalKtoRawSnapshotStore implements KtoRawSnapshotStore {

	private static final int HASH_BUFFER_BYTES = 8 * 1024;
	private static final Path WORKING_DIRECTORY = Path.of("").toAbsolutePath().normalize();

	private final Path rootDirectory;

	public LocalKtoRawSnapshotStore(KtoSnapshotProperties properties) {
		this.rootDirectory = properties.directory().toAbsolutePath().normalize();
	}

	@Override
	public KtoStoredSnapshotMetadata store(KtoRawSnapshot snapshot) {
		if (rootDirectory.startsWith(WORKING_DIRECTORY)) {
			throw new KtoSnapshotStorageException();
		}
		String storageKey = KtoSnapshotObjectKeyFactory.create(snapshot);
		Path target = rootDirectory.resolve(storageKey).normalize();
		if (!target.startsWith(rootDirectory)) {
			throw new KtoSnapshotStorageException();
		}

		try {
			if (Files.exists(target)) {
				return replay(snapshot, storageKey, target);
			}
			Files.createDirectories(target.getParent());
			Path temporary = Files.createTempFile(target.getParent(), ".kto-", ".json.gz.tmp");
			try {
				try (var output = new GZIPOutputStream(Files.newOutputStream(temporary))) {
					output.write(snapshot.payload());
				}
				moveAtomically(temporary, target);
			} finally {
				Files.deleteIfExists(temporary);
			}
			return metadata(snapshot, storageKey, target);
		} catch (KtoSnapshotConflictException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new KtoSnapshotStorageException();
		}
	}

	private KtoStoredSnapshotMetadata replay(
		KtoRawSnapshot snapshot,
		String storageKey,
		Path target
	) throws IOException {
		if (!snapshot.rawContentSha256().equals(uncompressedSha256(target))) {
			throw new KtoSnapshotConflictException();
		}
		return metadata(snapshot, storageKey, target);
	}

	private KtoStoredSnapshotMetadata metadata(
		KtoRawSnapshot snapshot,
		String storageKey,
		Path target
	) throws IOException {
		return new KtoStoredSnapshotMetadata(
			storageKey,
			sha256(Files.newInputStream(target)),
			Files.size(target),
			snapshot.capturedAt());
	}

	private void moveAtomically(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target);
		}
	}

	private String uncompressedSha256(Path path) throws IOException {
		try (var input = new GZIPInputStream(Files.newInputStream(path))) {
			return sha256(input);
		} catch (IOException exception) {
			throw new KtoSnapshotConflictException();
		}
	}

	private String sha256(InputStream input) throws IOException {
		try (input) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[HASH_BUFFER_BYTES];
			int bytesRead;
			while ((bytesRead = input.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
