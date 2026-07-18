package koready_backend.kto.infrastructure.config;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.snapshot.s3")
public record KtoS3SnapshotProperties(
	String bucket,
	String region,
	Duration connectTimeout,
	Duration readTimeout
) {

	private static final Pattern BUCKET =
		Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
	private static final Pattern REGION = Pattern.compile("[a-z]{2}(?:-gov)?-[a-z]+-\\d");

	public KtoS3SnapshotProperties {
		bucket = bucket == null ? "" : bucket.trim();
		region = region == null ? "" : region.trim();
		connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
		readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
	}

	public String requiredBucket() {
		if (!BUCKET.matcher(bucket).matches()
			|| bucket.contains("..")
			|| bucket.contains(".-")
			|| bucket.contains("-.")) {
			throw new IllegalStateException("KTO snapshot S3 bucket is invalid");
		}
		return bucket;
	}

	public String requiredRegion() {
		if (!REGION.matcher(region).matches()) {
			throw new IllegalStateException("KTO snapshot S3 region is invalid");
		}
		return region;
	}

	public Duration requiredConnectTimeout() {
		return positive(connectTimeout, "connect timeout");
	}

	public Duration requiredReadTimeout() {
		return positive(readTimeout, "read timeout");
	}

	private Duration positive(Duration value, String name) {
		Objects.requireNonNull(value, "KTO snapshot S3 " + name + " is required");
		if (value.isZero() || value.isNegative()) {
			throw new IllegalStateException("KTO snapshot S3 " + name + " must be positive");
		}
		return value;
	}
}
