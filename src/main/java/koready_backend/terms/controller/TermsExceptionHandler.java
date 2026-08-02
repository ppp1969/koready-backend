package koready_backend.terms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.terms.application.exception.InvalidTermAgreementException;
import koready_backend.terms.application.exception.RequiredTermsNotAgreedException;
import koready_backend.terms.application.exception.TermsUserUnavailableException;

@RestControllerAdvice(assignableTypes = TermsController.class)
public class TermsExceptionHandler {

	@ExceptionHandler(TermsUserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		TermsUserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNAUTHORIZED,
			"UNAUTHORIZED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(RequiredTermsNotAgreedException.class)
	ResponseEntity<ApiErrorResponse> handleRequiredTerms(
		RequiredTermsNotAgreedException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"REQUIRED_TERMS_NOT_AGREED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(InvalidTermAgreementException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidAgreement(
		InvalidTermAgreementException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_TERM_AGREEMENT",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(
		Exception exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"Check the request body.",
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
