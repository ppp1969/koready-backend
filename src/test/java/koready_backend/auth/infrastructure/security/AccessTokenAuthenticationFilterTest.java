package koready_backend.auth.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import koready_backend.auth.application.port.AccessTokenPort;
import koready_backend.auth.application.port.AccessTokenPort.AuthenticatedAccessToken;

@ExtendWith(MockitoExtension.class)
class AccessTokenAuthenticationFilterTest {

	@Mock
	private AccessTokenPort accessTokens;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticatesAValidKoReadyAccessTokenWithItsPublicUserId() throws Exception {
		when(accessTokens.verify("header.payload.signature"))
			.thenReturn(Optional.of(new AuthenticatedAccessToken(
				"usr_a1", Set.of("USER"))));

		Authentication authentication = filter("header.payload.signature");

		assertEquals("usr_a1", authentication.getName());
		assertEquals(
			Set.of("ROLE_USER"),
			authentication.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	void ignoresLocalHarnessTokensAndInvalidJwtValues() throws Exception {
		assertNull(filter("local-user"));
		SecurityContextHolder.clearContext();
		when(accessTokens.verify("header.payload.invalid"))
			.thenReturn(Optional.empty());
		assertNull(filter("header.payload.invalid"));
	}

	private Authentication filter(String token) throws Exception {
		var filter = new AccessTokenAuthenticationFilter(accessTokens);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<Authentication> captured = new AtomicReference<>();

		filter.doFilter(request, response, (servletRequest, servletResponse) ->
			captured.set(SecurityContextHolder.getContext().getAuthentication()));

		return captured.get();
	}
}
