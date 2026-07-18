package koready_backend.recommendation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository;
import koready_backend.recommendation.application.port.MonthlyRecommendationRepository.MonthlyRecommendationRow;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonthlyRecommendationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MonthlyRecommendationRepository repository;

	@BeforeEach
	void repositoryDefaults() {
		when(repository.count(any())).thenReturn(1L);
		when(repository.findPage(any())).thenReturn(List.of(new MonthlyRecommendationRow(
			71L,
			17L,
			2026,
			LocalDate.of(2026, 7, 1),
			LocalDate.of(2026, 7, 2),
			"Local Festival",
			ServiceRegionCode.SEOUL,
			"Seoul",
			"Jung-gu, Seoul",
			null,
			TravelStyle.LOCAL_FESTIVAL,
			"A local festival.",
			new BigDecimal("90.00"),
			2)));
	}

	@Test
	void returnsPublicMonthlyRecommendationEnvelope() throws Exception {
		mockMvc.perform(get("/api/v1/monthly-recommendations")
				.param("year", "2026")
				.param("month", "7")
				.param("travelStyles", "LOCAL_FESTIVAL")
				.header("Accept-Language", "en-US"))
			.andExpect(status().isOk())
			.andExpect(header().exists(TraceIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("MONTHLY_RECOMMENDATIONS_OK"))
			.andExpect(jsonPath("$.data.year").value(2026))
			.andExpect(jsonPath("$.data.month").value(7))
			.andExpect(jsonPath("$.data.appliedFilters.dateFilterType").value("ALL"))
			.andExpect(jsonPath("$.data.appliedFilters.travelStyles[0]")
				.value("LOCAL_FESTIVAL"))
			.andExpect(jsonPath("$.data.items", hasSize(1)))
			.andExpect(jsonPath("$.data.items[0].festivalOccurrence.occurrenceId")
				.value(71))
			.andExpect(jsonPath("$.data.items[0].festivalOccurrence.status")
				.value("ENDED"))
			.andExpect(jsonPath("$.data.items[0].saved").value(false))
			.andExpect(jsonPath("$.data.totalCount").value(1));
	}

	@Test
	void rejectsMissingRequiredValuesAndInvalidCustomRange() throws Exception {
		mockMvc.perform(get("/api/v1/monthly-recommendations")
				.param("month", "7"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/api/v1/monthly-recommendations")
				.param("year", "2026")
				.param("month", "7")
				.param("dateFilterType", "CUSTOM")
				.param("customStartDate", "2026-07-20")
				.param("customEndDate", "2026-07-10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
	}

	@Test
	void rejectsCursorFromOutsideTheCurrentQuery() throws Exception {
		mockMvc.perform(get("/api/v1/monthly-recommendations")
				.param("year", "2026")
				.param("month", "7")
				.param("cursor", "not-a-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
	}
}
