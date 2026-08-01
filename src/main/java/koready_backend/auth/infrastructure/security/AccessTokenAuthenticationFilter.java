package koready_backend.auth.infrastructure.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import koready_backend.auth.application.port.AccessTokenPort;

@Component
public final class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private final AccessTokenPort accessTokens;

	public AccessTokenAuthenticationFilter(AccessTokenPort accessTokens) {
		this.accessTokens = accessTokens;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticate(request);
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null
			|| !authorization.regionMatches(
				true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return;
		}
		String token = authorization.substring(BEARER_PREFIX.length());
		if (token.chars().filter(character -> character == '.').count() != 2) {
			return;
		}
		accessTokens.verify(token).ifPresent(authenticated -> {
			List<SimpleGrantedAuthority> authorities = authenticated.roles().stream()
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
			var authentication = UsernamePasswordAuthenticationToken.authenticated(
				authenticated.subject(), null, authorities);
			authentication.setDetails(
				new WebAuthenticationDetailsSource().buildDetails(request));
			var context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		});
	}
}
