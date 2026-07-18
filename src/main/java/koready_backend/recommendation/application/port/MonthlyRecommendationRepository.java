package koready_backend.recommendation.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.domain.RecommendationSort;

public interface MonthlyRecommendationRepository {

	List<MonthlyRecommendationRow> findPage(MonthlyRecommendationPageQuery query);

	long count(MonthlyRecommendationFilter filter);

	record MonthlyRecommendationFilter(
		LocalDate startDate,
		LocalDate endDate,
		LocalDate today,
		ServiceRegionCode serviceRegionCode,
		List<TravelStyle> travelStyles,
		PlaceLanguage language,
		RecommendationSort sort
	) {
		public MonthlyRecommendationFilter {
			travelStyles = List.copyOf(travelStyles);
		}
	}

	record MonthlyRecommendationPageQuery(
		MonthlyRecommendationFilter filter,
		MonthlyRecommendationCursor cursor,
		int limit
	) {
	}

	record MonthlyRecommendationCursor(
		int statusRank,
		BigDecimal qualityScore,
		LocalDate endDate,
		long occurrenceId
	) {
	}

	record MonthlyRecommendationRow(
		long occurrenceId,
		long placeId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String addressSummary,
		String imageUrl,
		TravelStyle travelStyle,
		String overview,
		BigDecimal qualityScore,
		int statusRank
	) {
	}
}
