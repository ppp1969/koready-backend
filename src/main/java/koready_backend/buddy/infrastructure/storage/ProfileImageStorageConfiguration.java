package koready_backend.buddy.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import koready_backend.buddy.application.exception.ProfileImageStorageUnavailableException;
import koready_backend.buddy.application.port.ProfileImageStorage;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
class ProfileImageStorageConfiguration {

	@Bean
	@ConditionalOnProperty(
		prefix = "koready.profile-image",
		name = "storage",
		havingValue = "s3")
	ProfileImageStorage profileImageS3Storage(ProfileImageStorageProperties properties) {
		Region region = Region.of(properties.requiredRegion());
		S3Client client = S3Client.builder()
			.region(region)
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.build();
		S3Presigner presigner = S3Presigner.builder().region(region).build();
		return new S3ProfileImageStorage(client, presigner, properties);
	}

	@Bean
	@ConditionalOnMissingBean(ProfileImageStorage.class)
	ProfileImageStorage unavailableProfileImageStorage() {
		return new ProfileImageStorage() {
			@Override
			public UploadTarget createUpload(
				String objectKey,
				String contentType,
				java.time.Instant now
			) {
				throw new ProfileImageStorageUnavailableException();
			}

			@Override
			public StoredObject inspect(String objectKey) {
				throw new ProfileImageStorageUnavailableException();
			}

			@Override
			public void delete(String objectKey) {
				throw new ProfileImageStorageUnavailableException();
			}

			@Override
			public String createViewUrl(String objectKey, java.time.Instant now) {
				throw new ProfileImageStorageUnavailableException();
			}
		};
	}
}
