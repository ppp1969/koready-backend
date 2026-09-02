package koready_backend.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.user.application.UserSessionService;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserSessionController {

	private final UserSessionService service;

	public UserSessionController(UserSessionService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<UserSessionDtos.MyUserResponse> get(
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"MY_USER_OK",
			UserSessionDtos.from(service.get(authentication.getName())),
			TraceIdFilter.current(request));
	}
}
