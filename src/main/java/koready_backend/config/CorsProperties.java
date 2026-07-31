package koready_backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.web.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
			? List.of("*")
			: List.copyOf(allowedOrigins);
	}
}
