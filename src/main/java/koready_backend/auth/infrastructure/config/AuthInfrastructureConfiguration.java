package koready_backend.auth.infrastructure.config;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import koready_backend.auth.application.AuthPolicy;
import koready_backend.auth.application.port.AccessTokenPort;
import koready_backend.auth.application.port.GoogleIdentityVerifier;
import koready_backend.auth.application.port.RefreshTokenCodec;
import koready_backend.auth.application.port.UserPublicIdGenerator;
import koready_backend.auth.infrastructure.google.GoogleIdTokenVerifier;
import koready_backend.auth.infrastructure.token.JwtAccessTokenService;
import koready_backend.auth.infrastructure.token.SecureRefreshTokenCodec;
import koready_backend.auth.infrastructure.token.UuidUserPublicIdGenerator;

@Configuration(proxyBeanMethods = false)
public class AuthInfrastructureConfiguration {

	@Bean
	GoogleIdentityVerifier googleIdentityVerifier(GoogleAuthProperties properties) {
		var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
		return new GoogleIdTokenVerifier(
			decoder,
			Set.copyOf(properties.clientIds()),
			properties.issuer());
	}

	@Bean
	AccessTokenPort accessTokenPort(JwtAuthProperties properties) {
		return new JwtAccessTokenService(
			properties.secret(),
			properties.issuer(),
			properties.audience(),
			properties.accessTokenTtl());
	}

	@Bean
	RefreshTokenCodec refreshTokenCodec() {
		return new SecureRefreshTokenCodec();
	}

	@Bean
	UserPublicIdGenerator userPublicIdGenerator() {
		return new UuidUserPublicIdGenerator();
	}

	@Bean
	AuthPolicy authPolicy(JwtAuthProperties properties) {
		return new AuthPolicy(properties.refreshTokenTtl());
	}
}
