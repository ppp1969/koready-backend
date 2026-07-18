package koready_backend.horitip.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
import koready_backend.horitip.application.exception.HoriTipCodeDuplicatedException;
import koready_backend.horitip.application.exception.HoriTipConcurrentModificationException;
import koready_backend.horitip.application.exception.HoriTipNotFoundException;
import koready_backend.horitip.application.exception.InvalidHoriTipCursorException;
import koready_backend.horitip.domain.HoriTipPolicyException;

@RestControllerAdvice(assignableTypes = AdminHoriTipController.class)
public class HoriTipExceptionHandler {

	@ExceptionHandler(HoriTipNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNotFound(
		HoriTipNotFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, "HORI_TIP_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidHoriTipCursorException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCursor(
		InvalidHoriTipCursorException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", exception.getMessage(), request);
	}

	@ExceptionHandler(HoriTipCodeDuplicatedException.class)
	ResponseEntity<ApiErrorResponse> handleDuplicatedCode(
		HoriTipCodeDuplicatedException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.CONFLICT, "HORI_TIP_CODE_DUPLICATED", exception.getMessage(), request);
	}

	@ExceptionHandler(HoriTipConcurrentModificationException.class)
	ResponseEntity<ApiErrorResponse> handleConcurrentModification(
		HoriTipConcurrentModificationException exception,
		HttpServletRequest request
	) {
		return error(
			HttpStatus.CONFLICT, "HORI_TIP_NOT_EDITABLE", exception.getMessage(), request);
	}

	@ExceptionHandler(HoriTipPolicyException.class)
	ResponseEntity<ApiErrorResponse> handlePolicy(
		HoriTipPolicyException exception,
		HttpServletRequest request
	) {
		return switch (exception.reason()) {
			case NOT_EDITABLE -> error(
				HttpStatus.CONFLICT,
				"HORI_TIP_NOT_EDITABLE",
				exception.getMessage(),
				request);
			case RULE_INVALID -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"HORI_TIP_RULE_INVALID",
				exception.getMessage(),
				request);
			case ACTIVATION_INVALID -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"HORI_TIP_ACTIVATION_INVALID",
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
		HttpMessageNotReadableException.class,
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
