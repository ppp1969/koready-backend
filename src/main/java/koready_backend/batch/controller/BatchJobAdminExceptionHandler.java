package koready_backend.batch.controller;

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
import koready_backend.batch.application.exception.BatchJobNotFoundException;
import koready_backend.batch.application.exception.InvalidBatchJobCursorException;
import koready_backend.batch.application.exception.InvalidBatchJobPeriodException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;

@RestControllerAdvice(assignableTypes = AdminBatchJobController.class)
public class BatchJobAdminExceptionHandler {

	@ExceptionHandler(BatchJobNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(
		BatchJobNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND, "BATCH_JOB_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidBatchJobCursorException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCursor(
		InvalidBatchJobCursorException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception.getMessage(), request);
	}

	@ExceptionHandler({
		InvalidBatchJobPeriodException.class,
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
