package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.exception.BuddyProfileNotFoundException;
import koready_backend.buddy.application.exception.BuddyProfileRequiredException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.MessageIdempotencyConflictException;
import koready_backend.buddy.application.exception.MessageNotAllowedException;
import koready_backend.buddy.application.exception.MessagePlaceNotFoundException;
import koready_backend.buddy.application.exception.MessageThreadNotFoundException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;

@RestControllerAdvice(assignableTypes = BuddyMessageController.class)
public class BuddyMessageExceptionHandler {

	@ExceptionHandler(BuddyUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		BuddyUserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
	}

	@ExceptionHandler(BuddyProfileNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleProfileNotFound(
		BuddyProfileNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"BUDDY_PROFILE_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(MessagePlaceNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handlePlaceNotFound(
		MessagePlaceNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(MessageThreadNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleThreadNotFound(
		MessageThreadNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"MESSAGE_THREAD_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(BuddyProfileRequiredException.class)
	ResponseEntity<ApiErrorResponse> handleProfileRequired(
		BuddyProfileRequiredException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_CONTENT,
			"BUDDY_PROFILE_REQUIRED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(MessageNotAllowedException.class)
	ResponseEntity<ApiErrorResponse> handleMessageNotAllowed(
		MessageNotAllowedException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_CONTENT,
			"MESSAGE_NOT_ALLOWED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(MessageIdempotencyConflictException.class)
	ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
		MessageIdempotencyConflictException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.CONFLICT,
			"IDEMPOTENCY_KEY_REUSED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class,
		MissingRequestHeaderException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(
		Exception exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"Check the request parameters, headers, and body.",
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
