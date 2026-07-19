package koready_backend.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class LocalDevAuthenticationFilterTest {

	private final LocalDevAuthenticationFilter filter = new LocalDevAuthenticationFilter();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@MethodSource("identities")
	void authenticatesOnlyTheFixedLocalIdentity(
		String token,
		String subject,
		String role
	) throws Exception {
		Authentication authentication = filter(token);

		assertEquals(subject, authentication.getName());
		assertEquals(
			Set.of("ROLE_" + role),
			authentication.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.collect(Collectors.toSet()));
	}

	@Test
	void leavesUnknownOrMalformedAuthorizationUnauthenticated() throws Exception {
		assertNull(filter("unknown-token"));
		SecurityContextHolder.clearContext();
		assertNull(filter("local-user extra"));
		SecurityContextHolder.clearContext();
		assertNull(filterWithoutBearerScheme("local-user"));
	}

	@Test
	void configurationExcludesStagingAndProductionEvenWithLocalProfile() {
		Profile profile = LocalDevSecurityConfiguration.class.getAnnotation(Profile.class);

		assertArrayEquals(
			new String[]{"local & !staging & !prod"},
			profile.value());
	}

	private Authentication filter(String token) throws Exception {
		return filterRequest("Bearer " + token);
	}

	private Authentication filterWithoutBearerScheme(String token) throws Exception {
		return filterRequest(token);
	}

	private Authentication filterRequest(String authorization) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<Authentication> captured = new AtomicReference<>();

		filter.doFilter(request, response, (servletRequest, servletResponse) ->
			captured.set(SecurityContextHolder.getContext().getAuthentication()));

		return captured.get();
	}

	private static java.util.stream.Stream<Arguments> identities() {
		return java.util.stream.Stream.of(
			Arguments.of("local-user", "local-user", "USER"),
			Arguments.of("local-operator", "local-operator", "OPERATOR"),
			Arguments.of("local-auditor", "local-auditor", "AUDITOR"),
			Arguments.of("local-admin", "local-admin", "ADMIN"));
	}
}
