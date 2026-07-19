package koready_backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = Type.SERVLET)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		ApiSecurityErrorHandler securityErrorHandler,
		ObjectProvider<LocalDevAuthenticationFilter> localDevAuthenticationFilter
	) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(securityErrorHandler)
				.accessDeniedHandler(securityErrorHandler))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					"/actuator/health", "/actuator/health/**",
					"/openapi/**", "/v3/api-docs/**",
					"/swagger-ui.html", "/swagger-ui/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/places/*/mates")
				.authenticated()
				.requestMatchers(HttpMethod.GET, "/api/v1/places", "/api/v1/places/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/monthly-recommendations")
				.permitAll()
				.anyRequest().authenticated());

		localDevAuthenticationFilter.ifAvailable(filter ->
			http.addFilterBefore(filter, AnonymousAuthenticationFilter.class));

		return http.build();
	}
}
