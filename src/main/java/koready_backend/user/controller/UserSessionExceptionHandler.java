package koready_backend.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.user.application.exception.UserUnavailableException;

@RestControllerAdvice(assignableTypes = UserSessionController.class)
public class UserSessionExceptionHandler {

	@ExceptionHandler(UserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailable(
		UserUnavailableException exception,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiErrorResponse(
			"UNAUTHORIZED", exception.getMessage(), TraceIdFilter.current(request)));
	}
}
