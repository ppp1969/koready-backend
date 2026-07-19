package koready_backend.evidence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.evidence.application.exception.EvidenceBundleDownloadUnavailableException;
import koready_backend.evidence.application.exception.EvidenceBundleNotCompletedException;
import koready_backend.evidence.application.exception.EvidenceBundleNotFoundException;
import koready_backend.evidence.application.exception.InvalidEvidenceBundlePeriodException;

@RestControllerAdvice(assignableTypes = AdminEvidenceBundleController.class)
class EvidenceBundleExceptionHandler {

	@ExceptionHandler(EvidenceBundleNotFoundException.class)
	ResponseEntity<ApiErrorResponse> missing(EvidenceBundleNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "EVIDENCE_BUNDLE_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(EvidenceBundleNotCompletedException.class)
	ResponseEntity<ApiErrorResponse> notCompleted(EvidenceBundleNotCompletedException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "EVIDENCE_BUNDLE_NOT_COMPLETED", exception.getMessage(), request);
	}

	@ExceptionHandler(EvidenceBundleDownloadUnavailableException.class)
	ResponseEntity<ApiErrorResponse> unavailable(EvidenceBundleDownloadUnavailableException exception, HttpServletRequest request) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, "EVIDENCE_BUNDLE_DOWNLOAD_UNAVAILABLE", exception.getMessage(), request);
	}

	@ExceptionHandler({ InvalidEvidenceBundlePeriodException.class, IllegalArgumentException.class,
		MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
		ConstraintViolationException.class, HttpMessageNotReadableException.class })
	ResponseEntity<ApiErrorResponse> invalid(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check the request parameters.", request);
	}

	private static ResponseEntity<ApiErrorResponse> error(
		HttpStatus status, String code, String message, HttpServletRequest request
	) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, TraceIdFilter.current(request)));
	}
}
