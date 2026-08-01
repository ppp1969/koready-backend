package koready_backend.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.auth.application.GoogleAuthService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final GoogleAuthService service;

	public AuthController(GoogleAuthService service) {
		this.service = service;
	}

	@PostMapping("/google")
	public ApiEnvelope<AuthDtos.TokenResponse> googleLogin(
		@RequestBody @Valid AuthDtos.GoogleLoginRequest body,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"GOOGLE_LOGIN_OK",
			AuthDtos.from(service.login(body.idToken(), body.deviceId())),
			TraceIdFilter.current(request));
	}

	@PostMapping("/refresh")
	public ApiEnvelope<AuthDtos.TokenResponse> refresh(
		@RequestBody @Valid AuthDtos.RefreshTokenRequest body,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"AUTH_TOKEN_REFRESHED",
			AuthDtos.from(service.refresh(body.refreshToken(), body.deviceId())),
			TraceIdFilter.current(request));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(
		@RequestBody @Valid AuthDtos.RefreshTokenRequest body,
		Authentication authentication
	) {
		service.logout(
			authentication.getName(),
			body.refreshToken(),
			body.deviceId());
	}
}
