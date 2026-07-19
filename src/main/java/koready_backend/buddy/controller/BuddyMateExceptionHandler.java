package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.InvalidMateCursorException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.exception.PlaceNotFoundException;

@RestControllerAdvice(assignableTypes = BuddyMateController.class)
public class BuddyMateExceptionHandler {

	@ExceptionHandler(BuddyUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		BuddyUserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
	}

	@ExceptionHandler(PlaceNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handlePlaceNotFound(
		PlaceNotFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidMateCursorException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCursor(
		InvalidMateCursorException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception.getMessage(), request);
	}

	@ExceptionHandler({
		HandlerMethodValidationException.class,
		ConstraintViolationException.class,
		MethodArgumentTypeMismatchException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(
		Exception exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"Check the request parameters.",
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
