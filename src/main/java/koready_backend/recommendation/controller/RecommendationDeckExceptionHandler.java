package koready_backend.recommendation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.recommendation.application.exception.RecommendationContextUnavailableException;
import koready_backend.recommendation.application.exception.RecommendationDeckNotFoundException;

@RestControllerAdvice(assignableTypes = RecommendationDeckController.class)
public class RecommendationDeckExceptionHandler {

	@ExceptionHandler(RecommendationContextUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableContext(
		RecommendationContextUnavailableException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"RECOMMENDATION_CONTEXT_UNAVAILABLE",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(RecommendationDeckNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleDeckNotFound(
		RecommendationDeckNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"RECOMMENDATION_DECK_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
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
