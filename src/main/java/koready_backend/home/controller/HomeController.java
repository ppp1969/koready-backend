package koready_backend.home.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.home.application.HomeService;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

	private final HomeService service;

	public HomeController(HomeService service) {
		this.service = service;
	}

	@GetMapping
	public ApiEnvelope<HomeDtos.HomeResponse> getHome(
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"HOME_OK",
			HomeDtos.from(service.getHome(authentication.getName())),
			TraceIdFilter.current(request));
	}
}
