package koready_backend.account.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import koready_backend.account.application.AccountWithdrawalService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/users/me/withdrawal")
public class AccountWithdrawalController {
	private final AccountWithdrawalService service;
	public AccountWithdrawalController(AccountWithdrawalService service) { this.service = service; }

	@GetMapping
	@Operation(summary = "회원 탈퇴 상태 조회", description = "탈퇴 신청·확정 시각과 쪽지 삭제 예정 시각을 조회합니다.")
	public ApiEnvelope<AccountWithdrawalDtos.Response> get(Authentication auth, HttpServletRequest request) {
		return response("ACCOUNT_WITHDRAWAL_STATUS_OK", service.get(auth.getName()), request);
	}

	@PostMapping
	@Operation(summary = "회원 탈퇴 신청", description = "즉시 프로필을 숨기고 서비스 이용을 제한합니다. 7일 뒤 탈퇴가 확정됩니다.")
	public ApiEnvelope<AccountWithdrawalDtos.Response> request(Authentication auth, HttpServletRequest request) {
		return response("ACCOUNT_WITHDRAWAL_REQUESTED", service.request(auth.getName()), request);
	}

	@DeleteMapping
	@Operation(summary = "회원 탈퇴 철회", description = "탈퇴 확정 전 7일 유예기간 안에만 철회할 수 있습니다.")
	public ApiEnvelope<AccountWithdrawalDtos.Response> cancel(Authentication auth, HttpServletRequest request) {
		return response("ACCOUNT_WITHDRAWAL_CANCELLED", service.cancel(auth.getName()), request);
	}

	private static ApiEnvelope<AccountWithdrawalDtos.Response> response(String code,
		koready_backend.account.application.port.AccountWithdrawalRepository.WithdrawalState state,
		HttpServletRequest request) {
		return ApiEnvelope.success(code, AccountWithdrawalDtos.from(state), TraceIdFilter.current(request));
	}
}
