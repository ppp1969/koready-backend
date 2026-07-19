package koready_backend.location.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.location.application.exception.LocationProviderUnavailableException;

@RestControllerAdvice(assignableTypes = LocationSearchController.class)
public class LocationSearchExceptionHandler {

	@ExceptionHandler(LocationProviderUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleProviderUnavailable(
		LocationProviderUnavailableException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.SERVICE_UNAVAILABLE,
			"LOCATION_PROVIDER_UNAVAILABLE",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class,
		HandlerMethodValidationException.class,
		ConstraintViolationException.class
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
