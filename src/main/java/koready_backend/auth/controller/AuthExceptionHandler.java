package koready_backend.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.auth.application.exception.AuthUnavailableException;
import koready_backend.auth.application.exception.InvalidGoogleIdTokenException;
import koready_backend.auth.application.exception.InvalidRefreshTokenException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

	@ExceptionHandler(InvalidGoogleIdTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidGoogleToken(
		InvalidGoogleIdTokenException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNAUTHORIZED,
			"GOOGLE_ID_TOKEN_INVALID",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
		InvalidRefreshTokenException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNAUTHORIZED,
			"REFRESH_TOKEN_INVALID",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(AuthUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailable(
		AuthUnavailableException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.SERVICE_UNAVAILABLE,
			"AUTH_UNAVAILABLE",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(
		Exception exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"Check the request body.",
			request);
	}

	private static ResponseEntity<ApiErrorResponse> error(
		HttpStatus status,
		String code,
		String message,
		HttpServletRequest request
	) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
			code, message, TraceIdFilter.current(request)));
	}
}
