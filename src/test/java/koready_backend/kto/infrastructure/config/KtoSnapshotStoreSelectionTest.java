package koready_backend.kto.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.infrastructure.snapshot.LocalKtoRawSnapshotStore;
import koready_backend.kto.infrastructure.snapshot.S3KtoRawSnapshotStore;

class KtoSnapshotStoreSelectionTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(SnapshotStoreConfiguration.class)
		.withPropertyValues(
			"koready.kto.snapshot.directory=" + Path.of(
				System.getProperty("java.io.tmpdir"), "koready-test-snapshots"));

	@Test
	void usesTheLocalStoreByDefault() {
		contextRunner.run(context -> {
			assertEquals(1, context.getBeansOfType(KtoRawSnapshotStore.class).size());
			assertInstanceOf(
				LocalKtoRawSnapshotStore.class,
				context.getBean(KtoRawSnapshotStore.class));
		});
	}

	@Test
	void usesOnlyTheS3StoreWhenExplicitlySelected() {
		contextRunner
			.withPropertyValues(
				"koready.kto.snapshot.storage=s3",
				"koready.kto.snapshot.s3.bucket=koready-kto-snapshots-test",
				"koready.kto.snapshot.s3.region=ap-northeast-2")
			.run(context -> {
				assertEquals(1, context.getBeansOfType(KtoRawSnapshotStore.class).size());
				assertInstanceOf(
					S3KtoRawSnapshotStore.class,
					context.getBean(KtoRawSnapshotStore.class));
			});
	}

	@Test
	void refusesToStartTheS3StoreWithoutABucket() {
		contextRunner
			.withPropertyValues(
				"koready.kto.snapshot.storage=s3",
				"koready.kto.snapshot.s3.region=ap-northeast-2")
			.run(context -> assertNotNull(context.getStartupFailure()));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties({
		KtoSnapshotProperties.class,
		KtoS3SnapshotProperties.class
	})
	@Import({
		KtoS3ClientConfiguration.class,
		LocalKtoRawSnapshotStore.class,
		S3KtoRawSnapshotStore.class
	})
	static class SnapshotStoreConfiguration {
	}
}
