package koready_backend.kto.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.kto.application.exception.KtoEnglishReviewCandidateRequiredException;
import koready_backend.kto.application.exception.KtoEnglishReviewConflictException;
import koready_backend.kto.application.exception.KtoEnglishReviewNotFoundException;
import koready_backend.kto.application.exception.KtoEnglishReviewSourceUnavailableException;

@RestControllerAdvice(assignableTypes = AdminKtoEnglishReviewController.class)
public class KtoEnglishReviewExceptionHandler {

	@ExceptionHandler(KtoEnglishReviewNotFoundException.class)
	ResponseEntity<ApiErrorResponse> notFound(
		KtoEnglishReviewNotFoundException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.NOT_FOUND,
			"KTO_ENGLISH_REVIEW_NOT_FOUND",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(KtoEnglishReviewConflictException.class)
	ResponseEntity<ApiErrorResponse> conflict(
		KtoEnglishReviewConflictException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.CONFLICT,
			"KTO_ENGLISH_REVIEW_CONFLICT",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(KtoEnglishReviewCandidateRequiredException.class)
	ResponseEntity<ApiErrorResponse> candidateRequired(
		KtoEnglishReviewCandidateRequiredException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.UNPROCESSABLE_ENTITY,
			"KTO_ENGLISH_REVIEW_CANDIDATE_REQUIRED",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler(KtoEnglishReviewSourceUnavailableException.class)
	ResponseEntity<ApiErrorResponse> sourceUnavailable(
		KtoEnglishReviewSourceUnavailableException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.SERVICE_UNAVAILABLE,
			"KTO_ENGLISH_REVIEW_SOURCE_UNAVAILABLE",
			exception.getMessage(),
			request);
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HandlerMethodValidationException.class,
		MethodArgumentTypeMismatchException.class,
		ConstraintViolationException.class,
		HttpMessageNotReadableException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ApiErrorResponse> invalid(
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
