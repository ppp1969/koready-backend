package koready_backend.location.controller;

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
import koready_backend.location.application.exception.ExpiredLocationSearchTokenException;
import koready_backend.location.application.exception.InvalidLocationSearchTokenException;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.application.exception.UserLocationNotFoundException;
import koready_backend.location.application.exception.UserLocationUserUnavailableException;

@RestControllerAdvice(assignableTypes = UserLocationController.class)
public class UserLocationExceptionHandler {

	@ExceptionHandler(UserLocationUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		UserLocationUserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
	}

	@ExceptionHandler(UserLocationNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(
		UserLocationNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"USER_LOCATION_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(ExpiredLocationSearchTokenException.class)
	ResponseEntity<ApiErrorResponse> handleExpiredToken(
		ExpiredLocationSearchTokenException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.GONE,
			"LOCATION_SEARCH_RESULT_EXPIRED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(InvalidLocationSearchTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidToken(
		InvalidLocationSearchTokenException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"LOCATION_SEARCH_RESULT_INVALID",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(LocationProviderUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableTokenService(
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
		HttpMessageNotReadableException.class,
		MethodArgumentNotValidException.class,
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
