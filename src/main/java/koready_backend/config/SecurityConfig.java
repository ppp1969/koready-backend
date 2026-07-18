package koready_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					"/actuator/health", "/actuator/health/**",
					"/openapi/**", "/v3/api-docs/**",
					"/swagger-ui.html", "/swagger-ui/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/places", "/api/v1/places/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/monthly-recommendations")
				.permitAll()
				.anyRequest().authenticated());

		return http.build();
	}
}
