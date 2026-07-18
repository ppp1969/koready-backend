package koready_backend.onboarding.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.onboarding.application.exception.CandidateSetConcurrentModificationException;
import koready_backend.onboarding.application.exception.CandidateSetCopySourceInvalidException;
import koready_backend.onboarding.application.exception.CandidateSetNotFoundException;
import koready_backend.onboarding.application.exception.InvalidCandidateSetCursorException;
import koready_backend.onboarding.domain.CandidateSetPolicyException;

@RestControllerAdvice(assignableTypes = {
	OnboardingCandidateSetController.class,
	AdminCandidateSetController.class
})
public class CandidateSetExceptionHandler {

	@ExceptionHandler(CandidateSetNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(
		CandidateSetNotFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, "CURATION_SET_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidCandidateSetCursorException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCursor(
		InvalidCandidateSetCursorException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception.getMessage(), request);
	}

	@ExceptionHandler(CandidateSetCopySourceInvalidException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCopySource(
		CandidateSetCopySourceInvalidException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"CURATION_COPY_SOURCE_INVALID",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(CandidateSetConcurrentModificationException.class)
	ResponseEntity<ApiErrorResponse> handleConcurrentModification(
		CandidateSetConcurrentModificationException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.CONFLICT,
			"CURATION_SET_NOT_EDITABLE",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(CandidateSetPolicyException.class)
	ResponseEntity<ApiErrorResponse> handlePolicy(
		CandidateSetPolicyException exception,
		HttpServletRequest request
	) {
		return switch (exception.reason()) {
			case NOT_EDITABLE -> error(
				HttpStatus.CONFLICT,
				"CURATION_SET_NOT_EDITABLE",
				exception.getMessage(),
				request);
			case REQUIRES_TEN_ITEMS -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"CURATION_SET_REQUIRES_TEN_ITEMS",
				exception.getMessage(),
				request);
			case PLACE_NOT_READY -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"CURATION_PLACE_NOT_READY",
				exception.getMessage(),
				request);
			case ITEM_DUPLICATED, TOO_MANY_ITEMS -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"CURATION_ITEM_DUPLICATED",
				exception.getMessage(),
				request);
		};
	}

	@ExceptionHandler({
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class,
		MethodArgumentNotValidException.class,
		HandlerMethodValidationException.class,
		ConstraintViolationException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidRequest(
		Exception exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"Check the request parameters and body.",
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
