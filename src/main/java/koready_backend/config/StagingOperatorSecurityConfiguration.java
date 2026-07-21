package koready_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("staging")
@ConditionalOnProperty(
	prefix = "koready.security.staging-operator",
	name = "enabled",
	havingValue = "true")
class StagingOperatorSecurityConfiguration {

	@Bean
	StagingOperatorAuthenticationFilter stagingOperatorAuthenticationFilter(
		@Value("${koready.security.staging-operator.token}") String token
	) {
		return new StagingOperatorAuthenticationFilter(token);
	}
}
