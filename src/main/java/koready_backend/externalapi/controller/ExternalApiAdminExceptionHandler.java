package koready_backend.externalapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.externalapi.application.exception.ExternalApiCallNotFoundException;
import koready_backend.externalapi.application.exception.InvalidExternalApiCursorException;
import koready_backend.externalapi.application.exception.InvalidExternalApiPeriodException;
import koready_backend.externalapi.application.exception.RawSnapshotNotFoundException;
import koready_backend.externalapi.application.exception.SyncCursorNotFoundException;

@RestControllerAdvice(assignableTypes = AdminExternalApiController.class)
public class ExternalApiAdminExceptionHandler {

	@ExceptionHandler(ExternalApiCallNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleMissingCall(
		ExternalApiCallNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND, "OPEN_API_CALL_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(RawSnapshotNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleMissingSnapshot(
		RawSnapshotNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND, "RAW_SNAPSHOT_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(SyncCursorNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleMissingSyncCursor(
		SyncCursorNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND, "SYNC_CURSOR_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidExternalApiCursorException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCursor(
		InvalidExternalApiCursorException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception.getMessage(), request);
	}

	@ExceptionHandler({
		InvalidExternalApiPeriodException.class,
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class,
		MethodArgumentNotValidException.class,
		HandlerMethodValidationException.class,
		ConstraintViolationException.class,
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
