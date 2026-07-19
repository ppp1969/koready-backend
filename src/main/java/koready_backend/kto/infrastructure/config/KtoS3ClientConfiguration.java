package koready_backend.kto.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	prefix = "koready.kto.snapshot",
	name = "storage",
	havingValue = "s3")
class KtoS3ClientConfiguration {

	@Bean
	S3Client ktoSnapshotS3Client(KtoS3SnapshotProperties properties) {
		return S3Client.builder()
			.region(Region.of(properties.requiredRegion()))
			.httpClientBuilder(UrlConnectionHttpClient.builder()
				.connectionTimeout(properties.requiredConnectTimeout())
				.socketTimeout(properties.requiredReadTimeout()))
			.build();
	}

	@Bean
	S3Presigner ktoSnapshotS3Presigner(KtoS3SnapshotProperties properties) {
		return S3Presigner.builder()
			.region(Region.of(properties.requiredRegion()))
			.build();
	}
}
