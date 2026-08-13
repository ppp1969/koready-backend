package koready_backend.editorial.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
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
}
