package koready_backend.onboarding.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.onboarding.application.exception.OnboardingCompletionException;
import koready_backend.user.application.exception.UserUnavailableException;

@RestControllerAdvice(assignableTypes = OnboardingController.class)
public class OnboardingExceptionHandler {

	@ExceptionHandler(UserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleUnavailableUser(
		UserUnavailableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request);
	}

	@ExceptionHandler(OnboardingCompletionException.class)
	ResponseEntity<ApiErrorResponse> handleCompletion(
		OnboardingCompletionException exception,
		HttpServletRequest request
	) {
		return switch (exception.reason()) {
			case ALREADY_COMPLETED -> error(
				HttpStatus.CONFLICT,
				"ONBOARDING_ALREADY_COMPLETED",
				exception.getMessage(),
				request);
			case TRAVEL_STYLES_INVALID -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"ONBOARDING_TRAVEL_STYLES_INVALID",
				exception.getMessage(),
				request);
			case LOCATION_INVALID -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"ONBOARDING_LOCATION_INVALID",
				exception.getMessage(),
				request);
			case CANDIDATE_SET_INVALID, CANDIDATE_SET_VERSION_MISMATCH -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"ONBOARDING_CANDIDATE_SET_INVALID",
				exception.getMessage(),
				request);
			case SELECTION_INVALID -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"ONBOARDING_SELECTION_INVALID",
				exception.getMessage(),
				request);
			case PREREQUISITE_INCOMPLETE -> error(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"ONBOARDING_PREREQUISITE_INCOMPLETE",
				exception.getMessage(),
				request);
		};
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
