package koready_backend.editorial.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.editorial.application.exception.EditorialPlaceNotFoundException;

@RestControllerAdvice(assignableTypes = AdminEditorialController.class)
class EditorialExceptionHandler {

	@ExceptionHandler(EditorialPlaceNotFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound(
		EditorialPlaceNotFoundException exception,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(
			"EDITORIAL_PLACE_NOT_FOUND", exception.getMessage(),
			TraceIdFilter.current(request)));
	}

	@ExceptionHandler({
		HandlerMethodValidationException.class,
		ConstraintViolationException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ApiErrorResponse> invalidRequest(
		Exception exception,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(
			"INVALID_REQUEST", "Check the request parameters.",
			TraceIdFilter.current(request)));
	}
}
