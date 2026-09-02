package koready_backend.terms.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.terms.application.AdminTermConflictException;
import koready_backend.terms.application.AdminTermNotFoundException;

@RestControllerAdvice(assignableTypes=AdminTermsController.class)
public class AdminTermsExceptionHandler {
	@ExceptionHandler(AdminTermNotFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound(RuntimeException e, HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, "ADMIN_TERM_NOT_FOUND", e, request); }
	@ExceptionHandler({AdminTermConflictException.class, DataIntegrityViolationException.class})
	ResponseEntity<ApiErrorResponse> conflict(RuntimeException e, HttpServletRequest request) { return error(HttpStatus.CONFLICT, "ADMIN_TERM_CONFLICT", e, request); }
	@ExceptionHandler({IllegalArgumentException.class})
	ResponseEntity<ApiErrorResponse> invalid(RuntimeException e, HttpServletRequest request) { return error(HttpStatus.BAD_REQUEST, "ADMIN_TERM_INVALID", e, request); }
	private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, RuntimeException e, HttpServletRequest request) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(code, e.getMessage(), TraceIdFilter.current(request)));
	}
}
