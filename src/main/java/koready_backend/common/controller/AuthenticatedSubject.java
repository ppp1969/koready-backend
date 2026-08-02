package koready_backend.common.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class AuthenticatedSubject {

	private AuthenticatedSubject() {
	}

	public static String optional(Authentication authentication) {
		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return authentication.getName();
	}
}
