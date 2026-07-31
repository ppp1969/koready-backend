package koready_backend.buddy.infrastructure.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.profile-image")
public record ProfileImageStorageProperties(
	String storage,
	String bucket,
	String region,
	Duration uploadExpiration,
	Duration viewExpiration
) {

	public ProfileImageStorageProperties {
		storage = storage == null ? "disabled" : storage.trim();
		bucket = bucket == null ? "" : bucket.trim();
		region = region == null ? "ap-northeast-2" : region.trim();
		uploadExpiration = uploadExpiration == null
			? Duration.ofMinutes(10)
			: uploadExpiration;
		viewExpiration = viewExpiration == null
			? Duration.ofMinutes(5)
			: viewExpiration;
	}

	String requiredBucket() {
		if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) {
			throw new IllegalStateException("Profile image S3 bucket is invalid");
		}
		return bucket;
	}

	String requiredRegion() {
		if (!region.matches("[a-z]{2}(?:-gov)?-[a-z]+-\\d")) {
			throw new IllegalStateException("Profile image S3 region is invalid");
		}
		return region;
	}

	Duration requiredUploadExpiration() {
		return bounded(uploadExpiration, Duration.ofMinutes(1), Duration.ofMinutes(15));
	}

	Duration requiredViewExpiration() {
		return bounded(viewExpiration, Duration.ofMinutes(1), Duration.ofMinutes(15));
	}

	private Duration bounded(Duration value, Duration minimum, Duration maximum) {
		if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
			throw new IllegalStateException(
				"Profile image signed URL expiration must be 1 to 15 minutes");
		}
		return value;
	}
}
