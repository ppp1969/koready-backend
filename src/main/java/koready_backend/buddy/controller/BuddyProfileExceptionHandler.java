package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;

@RestControllerAdvice(assignableTypes = BuddyProfileController.class)
public class BuddyProfileExceptionHandler {

	@ExceptionHandler(BuddyUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		BuddyUserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class,
		IllegalArgumentException.class
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
