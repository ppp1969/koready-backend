package koready_backend.recommendation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;
import koready_backend.recommendation.application.RecommendationDeckService;
import koready_backend.recommendation.application.exception.RecommendationContextUnavailableException;
import koready_backend.recommendation.application.exception.RecommendationDeckNotFoundException;
import koready_backend.recommendation.domain.RecommendationScope;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationDeckControllerTest {

	private static final String USER_PUBLIC_ID = "usr_recommendation_controller";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RecommendationDeckService service;

	@BeforeEach
	void serviceDefaults() {
		when(service.createDeck(
			eq(USER_PUBLIC_ID),
			eq(RecommendationScope.NEARBY),
			eq(10L),
			eq(2),
			any()))
			.thenReturn(page());
		when(service.getPage(USER_PUBLIC_ID, "rec_test", "next-cursor"))
			.thenReturn(page());
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/recommendation-decks")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"scope":"NATIONWIDE","size":20}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void createsARecommendationDeckForTheAuthenticatedUser() throws Exception {
		mockMvc.perform(post("/api/v1/recommendation-decks")
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.header("Accept-Language", "en-US")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"scope":"NEARBY","originLocationId":10,"size":2}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().exists(TraceIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_DECK_CREATED"))
			.andExpect(jsonPath("$.data.deckId").value("rec_test"))
			.andExpect(jsonPath("$.data.scope").value("NEARBY"))
			.andExpect(jsonPath("$.data.originLocation.locationId").value(10))
			.andExpect(jsonPath("$.data.cards", hasSize(1)))
			.andExpect(jsonPath("$.data.cards[0].matchRank").value(2))
			.andExpect(jsonPath("$.data.cards[0].saved").value(false))
			.andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
			.andExpect(jsonPath("$.data.deduplication.guaranteedWithinDeck").value(true))
			.andExpect(jsonPath("$.data.deduplication.suppressionDays").value(30));
	}

	@Test
	void returnsTheStableNextPage() throws Exception {
		mockMvc.perform(get("/api/v1/recommendation-decks/rec_test")
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.param("cursor", "next-cursor"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_DECK_OK"))
			.andExpect(jsonPath("$.data.deckId").value("rec_test"));
	}

	@Test
	void rejectsInvalidBodyAndUnavailableContext() throws Exception {
		mockMvc.perform(post("/api/v1/recommendation-decks")
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"scope":"NEARBY","size":0}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		when(service.createDeck(
			eq(USER_PUBLIC_ID),
			eq(RecommendationScope.NATIONWIDE),
			eq(null),
			eq(20),
			any()))
			.thenThrow(new RecommendationContextUnavailableException());
		mockMvc.perform(post("/api/v1/recommendation-decks")
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"scope":"NATIONWIDE","size":20}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_CONTEXT_UNAVAILABLE"));
	}

	@Test
	void hidesMissingOrForeignDecks() throws Exception {
		when(service.getPage(USER_PUBLIC_ID, "rec_missing", null))
			.thenThrow(new RecommendationDeckNotFoundException());

		mockMvc.perform(get("/api/v1/recommendation-decks/rec_missing")
				.with(user(USER_PUBLIC_ID).roles("USER")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_DECK_NOT_FOUND"));
	}

	private RecommendationDeckService.RecommendationDeckPage page() {
		return new RecommendationDeckService.RecommendationDeckPage(
			"rec_test",
			RecommendationScope.NEARBY,
			new RecommendationDeckService.LocationSummary(
				10L,
				"Campus",
				ServiceRegionCode.SEOUL),
			List.of(new RecommendationDeckService.RecommendationCard(
				1L,
				"Local place",
				"Seoul",
				null,
				false,
				List.of("NATURE"),
				"Description",
				ServiceRegionCode.SEOUL,
				TravelStyle.NATURE,
				2,
				new RecommendationDeckService.MatchReason(true, false, List.of()))),
			"next-cursor",
			true,
			5,
			new RecommendationDeckService.Deduplication(true, 30));
	}
}
