package koready_backend.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class LocalDevAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final Map<String, LocalIdentity> IDENTITIES = Map.of(
		"local-user", new LocalIdentity("local-user", "USER"),
		"local-operator", new LocalIdentity("local-operator", "OPERATOR"),
		"local-auditor", new LocalIdentity("local-auditor", "AUDITOR"),
		"local-admin", new LocalIdentity("local-admin", "ADMIN"));

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			authenticateFixedIdentity(request);
		}
		filterChain.doFilter(request, response);
	}

	private static void authenticateFixedIdentity(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null
			|| !authorization.regionMatches(true, 0, BEARER_PREFIX, 0,
				BEARER_PREFIX.length())) {
			return;
		}
		String token = authorization.substring(BEARER_PREFIX.length());
		LocalIdentity identity = IDENTITIES.get(token);
		if (identity == null) {
			return;
		}

		var authentication = UsernamePasswordAuthenticationToken.authenticated(
			identity.subject(),
			null,
			List.of(new SimpleGrantedAuthority("ROLE_" + identity.role())));
		authentication.setDetails(
			new WebAuthenticationDetailsSource().buildDetails(request));
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
	}

	private record LocalIdentity(String subject, String role) {
	}
}
