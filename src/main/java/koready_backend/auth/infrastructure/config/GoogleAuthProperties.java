package koready_backend.auth.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.security.google")
public record GoogleAuthProperties(
	String issuer,
	String jwkSetUri,
	List<String> clientIds
) {

	private static final String DEFAULT_ISSUER = "https://accounts.google.com";
	private static final String DEFAULT_JWK_SET_URI =
		"https://www.googleapis.com/oauth2/v3/certs";

	public GoogleAuthProperties {
		issuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer.strip();
		jwkSetUri = jwkSetUri == null || jwkSetUri.isBlank()
			? DEFAULT_JWK_SET_URI
			: jwkSetUri.strip();
		clientIds = clientIds == null
			? List.of()
			: clientIds.stream()
				.filter(value -> value != null && !value.isBlank())
				.map(String::strip)
				.distinct()
				.toList();
	}
}
