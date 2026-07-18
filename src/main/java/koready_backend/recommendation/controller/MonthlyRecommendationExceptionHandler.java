package koready_backend.recommendation.controller;

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
import koready_backend.recommendation.application.exception.InvalidDateRangeException;
import koready_backend.recommendation.application.exception.InvalidRecommendationCursorException;

@RestControllerAdvice(assignableTypes = MonthlyRecommendationController.class)
public class MonthlyRecommendationExceptionHandler {

	@ExceptionHandler(InvalidDateRangeException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidDateRange(
		InvalidDateRangeException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_DATE_RANGE",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(InvalidRecommendationCursorException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCursor(
		InvalidRecommendationCursorException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_CURSOR",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
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
