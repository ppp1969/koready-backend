package koready_backend.recommendation.infrastructure.persistence;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.port.RecommendationDeckRepository.CardSnapshot;
import tools.jackson.databind.json.JsonMapper;

class JdbcRecommendationDeckBatchWriteTest {

	@Test
	void insertsDeckItemsWithOneJdbcBatch() throws Exception {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		JdbcRecommendationDeckRepository repository = new JdbcRecommendationDeckRepository(
			jdbcTemplate,
			JsonMapper.builder().build());
		Method insertItems = JdbcRecommendationDeckRepository.class
			.getDeclaredMethod("insertItems", long.class, List.class);
		insertItems.setAccessible(true);

		insertItems.invoke(repository, 31L, List.of(card(101L), card(102L)));

		verify(jdbcTemplate).batchUpdate(anyString(), anyList());
	}

	private static CardSnapshot card(long placeId) {
		return new CardSnapshot(
			placeId,
			"Place " + placeId,
			"Seoul",
			"https://images.example.com/" + placeId + ".jpg",
			"Description",
			ServiceRegionCode.SEOUL,
			TravelStyle.NATURE,
			List.of(TravelStyle.NATURE.name()),
			2,
			true,
			false,
			List.of());
	}
}
