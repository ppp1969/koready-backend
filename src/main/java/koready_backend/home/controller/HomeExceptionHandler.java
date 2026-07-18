package koready_backend.home.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.home.application.exception.HomeUserUnavailableException;

@RestControllerAdvice(assignableTypes = HomeController.class)
public class HomeExceptionHandler {

	@ExceptionHandler(HomeUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		HomeUserUnavailableException exception,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiErrorResponse(
			"UNAUTHORIZED",
			exception.getMessage(),
			TraceIdFilter.current(request)));
	}
}
