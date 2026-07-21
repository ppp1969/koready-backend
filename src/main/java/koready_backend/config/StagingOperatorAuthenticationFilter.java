package koready_backend.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class StagingOperatorAuthenticationFilter extends OncePerRequestFilter {

	static final String TOKEN_HEADER = "X-Koready-Operator-Token";

	private final byte[] expectedToken;

	StagingOperatorAuthenticationFilter(String token) {
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("Staging operator token must not be blank");
		}
		this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/v1/admin/");
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null && tokenMatches(request)) {
			var authentication = UsernamePasswordAuthenticationToken.authenticated(
				"staging-operator",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		}
		filterChain.doFilter(request, response);
	}

	private boolean tokenMatches(HttpServletRequest request) {
		String suppliedToken = request.getHeader(TOKEN_HEADER);
		return suppliedToken != null && MessageDigest.isEqual(
			expectedToken, suppliedToken.getBytes(StandardCharsets.UTF_8));
	}
}
