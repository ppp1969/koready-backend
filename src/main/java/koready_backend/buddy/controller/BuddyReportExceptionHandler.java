package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.ReportIdempotencyConflictException;
import koready_backend.buddy.application.exception.ReportNotAllowedException;
import koready_backend.buddy.application.exception.ReportTargetNotFoundException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;

@RestControllerAdvice(assignableTypes = BuddyReportController.class)
public class BuddyReportExceptionHandler {

	@ExceptionHandler(BuddyUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		BuddyUserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
	}

	@ExceptionHandler(ReportTargetNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleTargetNotFound(
		ReportTargetNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"REPORT_TARGET_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(ReportNotAllowedException.class)
	ResponseEntity<ApiErrorResponse> handleNotAllowed(
		ReportNotAllowedException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_CONTENT,
			"REPORT_NOT_ALLOWED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(ReportIdempotencyConflictException.class)
	ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
		ReportIdempotencyConflictException exception,
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
