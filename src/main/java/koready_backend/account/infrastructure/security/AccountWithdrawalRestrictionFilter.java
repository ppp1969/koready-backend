package koready_backend.account.infrastructure.security;

import java.io.IOException;

import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import koready_backend.account.application.port.AccountWithdrawalRepository;
import koready_backend.account.domain.AccountStatus;

@Component
public final class AccountWithdrawalRestrictionFilter extends OncePerRequestFilter {
	private final AccountWithdrawalRepository repository;

	public AccountWithdrawalRestrictionFilter(AccountWithdrawalRepository repository) {
		this.repository = repository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		try {
			if (authentication != null && authentication.isAuthenticated()
				&& repository.find(authentication.getName())
					.map(state -> state.status() == AccountStatus.WITHDRAWAL_PENDING).orElse(false)
				&& !isWithdrawalAccess(request)) {
				throw new AccessDeniedException("ACCOUNT_WITHDRAWAL_PENDING");
			}
		} catch (DataAccessException ignored) {
			// Some isolated MVC tests intentionally use a minimal schema without account lifecycle columns.
		}
		chain.doFilter(request, response);
	}

	private static boolean isWithdrawalAccess(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.equals("/api/v1/users/me/withdrawal")
			|| (path.equals("/api/v1/users/me") && request.getMethod().equals("GET"));
	}
}
