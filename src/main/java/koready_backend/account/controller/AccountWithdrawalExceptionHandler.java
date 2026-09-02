package koready_backend.account.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.user.application.exception.UserUnavailableException;

@RestControllerAdvice(assignableTypes = AccountWithdrawalController.class)
public class AccountWithdrawalExceptionHandler {
	@ExceptionHandler(UserUnavailableException.class)
	ResponseEntity<ApiErrorResponse> unavailable(UserUnavailableException exception, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
			"ACCOUNT_WITHDRAWAL_UNAVAILABLE", "Withdrawal cannot be changed in the current state.",
			TraceIdFilter.current(request)));
	}
}
