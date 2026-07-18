package koready_backend.home.application;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.home.application.exception.HomeUserUnavailableException;
import koready_backend.home.application.port.HomeRepository;
import koready_backend.home.application.port.HomeRepository.HomeLocation;
import koready_backend.home.application.port.HomeRepository.HomeUser;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.recommendation.application.MonthlyRecommendationService;
import koready_backend.recommendation.domain.DateFilterType;
import koready_backend.recommendation.domain.RecommendationSort;

@Service
public class HomeService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int PREVIEW_SIZE = 5;

	private final HomeRepository repository;
	private final MonthlyRecommendationService monthlyRecommendationService;
	private final Clock clock;

	@Autowired
	public HomeService(
		HomeRepository repository,
		MonthlyRecommendationService monthlyRecommendationService
	) {
		this(repository, monthlyRecommendationService, Clock.system(SEOUL_ZONE));
	}

	HomeService(
		HomeRepository repository,
		MonthlyRecommendationService monthlyRecommendationService,
		Clock clock
	) {
		this.repository = repository;
		this.monthlyRecommendationService = monthlyRecommendationService;
		this.clock = clock;
	}

	public Home getHome(String userPublicId) {
		HomeUser user = repository.findByPublicId(userPublicId)
			.orElseThrow(HomeUserUnavailableException::new);
		YearMonth currentMonth = YearMonth.now(clock);
		MonthlyRecommendationPreview preview = user.currentLocation() == null
			? emptyPreview(currentMonth, user.preferredLanguage())
			: preview(currentMonth, user);
		return new Home(user.currentLocation(), user.preferredLanguage(), preview);
	}

	private MonthlyRecommendationPreview preview(YearMonth currentMonth, HomeUser user) {
		var page = monthlyRecommendationService.getMonthlyRecommendations(
			currentMonth.getYear(),
			currentMonth.getMonthValue(),
			user.currentLocation().serviceRegionCode(),
			DateFilterType.ALL,
			null,
			null,
			List.of(),
			RecommendationSort.RECOMMENDED,
			null,
			PREVIEW_SIZE,
			user.preferredLanguage());
		return new MonthlyRecommendationPreview(
			currentMonth.getYear(),
			currentMonth.getMonthValue(),
			title(currentMonth, user.preferredLanguage()),
			page.totalCount(),
			page.items());
	}

	private static MonthlyRecommendationPreview emptyPreview(
		YearMonth currentMonth,
		PlaceLanguage language
	) {
		return new MonthlyRecommendationPreview(
			currentMonth.getYear(),
			currentMonth.getMonthValue(),
			title(currentMonth, language),
			0L,
			List.of());
	}

	private static String title(YearMonth month, PlaceLanguage language) {
		if (language == PlaceLanguage.EN) {
			String englishMonth = month.getMonth()
				.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
			return englishMonth + " picks you should not miss!";
		}
		return month.getMonthValue() + "월엔 이건 해야지!";
	}

	public record Home(
		HomeLocation currentLocation,
		PlaceLanguage preferredLanguage,
		MonthlyRecommendationPreview monthlyRecommendation
	) {
	}

	public record MonthlyRecommendationPreview(
		int year,
		int month,
		String title,
		long totalCount,
		List<MonthlyRecommendationService.PlaceCard> items
	) {
		public MonthlyRecommendationPreview {
			items = List.copyOf(items);
		}
	}
}
