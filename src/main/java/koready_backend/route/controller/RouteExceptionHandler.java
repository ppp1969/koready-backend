package koready_backend.route.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.route.application.exception.RouteException;

@RestControllerAdvice(assignableTypes = RouteController.class)
public class RouteExceptionHandler {

	@ExceptionHandler(RouteException.class)
	ResponseEntity<ApiErrorResponse> handleRoute(
		RouteException exception,
		HttpServletRequest request
	) {
		HttpStatus status = switch (exception.reason()) {
			case CONTEXT_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case ROUTE_NOT_FOUND, ROUTE_NOT_AVAILABLE_AT_DEPARTURE_TIME ->
				HttpStatus.UNPROCESSABLE_ENTITY;
			case ROUTE_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
			case ROUTE_EXPIRED -> HttpStatus.GONE;
		};
		return error(status, exception.reason().name(), exception.getMessage(), request);
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		MethodArgumentNotValidException.class,
		ConstraintViolationException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalid(
		Exception exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
			"Check the request parameters.", request);
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
