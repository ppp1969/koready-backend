package koready_backend.kto.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class KtoRequestQuotaPropertiesTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withUserConfiguration(Config.class);

	@Test
	void applicationYamlBindsDefaultWithoutAnOverrideMap() {
		context.run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx.getBean(KtoRequestQuotaProperties.class).limit("kor-detailcommon2"))
				.isEqualTo(1000);
		});
	}

	@Test
	void operationOverrideDoesNotChangeOtherOperations() {
		context.withPropertyValues("koready.kto.quota.limits.kor-detailcommon2=10000")
			.run(ctx -> {
				assertThat(ctx).hasNotFailed();
				KtoRequestQuotaProperties properties = ctx.getBean(KtoRequestQuotaProperties.class);
				assertThat(properties.limit("kor-detailcommon2")).isEqualTo(10000);
				assertThat(properties.limit("kor-detailimage2")).isEqualTo(1000);
			});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(KtoRequestQuotaProperties.class)
	static class Config { }
}
