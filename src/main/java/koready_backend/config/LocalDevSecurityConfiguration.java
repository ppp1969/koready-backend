package koready_backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local & !staging & !prod")
@ConditionalOnProperty(
	prefix = "koready.security.dev-principal",
	name = "enabled",
	havingValue = "true")
class LocalDevSecurityConfiguration {

	@Bean
	LocalDevAuthenticationFilter localDevAuthenticationFilter() {
		return new LocalDevAuthenticationFilter();
	}
}
