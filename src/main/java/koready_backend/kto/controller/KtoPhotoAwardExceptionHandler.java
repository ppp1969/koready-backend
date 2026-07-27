package koready_backend.kto.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.kto.application.exception.KtoPhotoAwardMappingConflictException;
import koready_backend.kto.application.exception.KtoPhotoAwardNotFoundException;

@RestControllerAdvice(assignableTypes = AdminKtoPhotoAwardController.class)
public class KtoPhotoAwardExceptionHandler {

	@ExceptionHandler(KtoPhotoAwardNotFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound(
		KtoPhotoAwardNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"KTO_PHOTO_AWARD_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(KtoPhotoAwardMappingConflictException.class)
	ResponseEntity<ApiErrorResponse> conflict(
		KtoPhotoAwardMappingConflictException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.CONFLICT,
			"KTO_PHOTO_AWARD_MAPPING_CONFLICT",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HandlerMethodValidationException.class,
		MethodArgumentTypeMismatchException.class,
		ConstraintViolationException.class,
		HttpMessageNotReadableException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ApiErrorResponse> invalid(
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
