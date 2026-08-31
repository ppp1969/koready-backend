package koready_backend.home.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;
import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialLanguage;
import koready_backend.home.application.HomeService;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

	private final HomeService service;
	private final EditorialService editorialService;

	public HomeController(HomeService service, EditorialService editorialService) {
		this.service = service;
		this.editorialService = editorialService;
	}

	@GetMapping
	public ApiEnvelope<HomeDtos.HomeResponse> getHome(
		Authentication authentication,
		HttpServletRequest request
	) {
		var home = service.getHome(authentication.getName());
		var editorialContents = editorialService.findReadyCardContents(
			home.monthlyRecommendation().items().stream()
				.map(koready_backend.recommendation.application.MonthlyRecommendationService.PlaceCard::placeId)
				.toList(),
			EditorialLanguage.valueOf(home.preferredLanguage().name()));
		return ApiEnvelope.success(
			"HOME_OK",
			HomeDtos.from(home, editorialContents),
			TraceIdFilter.current(request));
	}
}
