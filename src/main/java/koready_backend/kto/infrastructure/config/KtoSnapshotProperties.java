package koready_backend.kto.infrastructure.config;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.snapshot")
public record KtoSnapshotProperties(Path directory) {

	public KtoSnapshotProperties {
		Objects.requireNonNull(directory, "KTO snapshot directory is required");
	}
}
