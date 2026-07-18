package koready_backend.recommendation.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import koready_backend.recommendation.application.RecommendationDeckService;
import koready_backend.recommendation.application.RecommendationEventService;
import koready_backend.recommendation.application.exception.RecommendationDeckNotFoundException;
import koready_backend.recommendation.domain.RecommendationEventType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationEventControllerTest {

	private static final String USER_PUBLIC_ID = "usr_event_controller";
	private static final String DECK_PUBLIC_ID = "rec_event_controller";
	private static final Instant OCCURRED_AT = Instant.parse("2026-07-19T04:00:00Z");
	private static final Instant RECORDED_AT = Instant.parse("2026-07-19T04:00:01Z");

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	RecommendationDeckService deckService;

	@MockitoBean
	RecommendationEventService eventService;

	@BeforeEach
	void serviceDefaults() {
		when(eventService.recordEvent(
			USER_PUBLIC_ID,
			DECK_PUBLIC_ID,
			42L,
			RecommendationEventType.PLACE_SAVED,
			OCCURRED_AT))
			.thenReturn(new RecommendationEventService.RecommendationEvent(
				"recevt_controller",
				DECK_PUBLIC_ID,
				42L,
				RecommendationEventType.PLACE_SAVED,
				RECORDED_AT));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/recommendation-decks/{deckId}/events", DECK_PUBLIC_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validBody()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void recordsAnEventForAServedCard() throws Exception {
		mockMvc.perform(post("/api/v1/recommendation-decks/{deckId}/events", DECK_PUBLIC_ID)
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validBody()))
			.andExpect(status().isCreated())
			.andExpect(header().exists(TraceIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_EVENT_CREATED"))
			.andExpect(jsonPath("$.data.eventId").value("recevt_controller"))
			.andExpect(jsonPath("$.data.deckId").value(DECK_PUBLIC_ID))
			.andExpect(jsonPath("$.data.placeId").value(42))
			.andExpect(jsonPath("$.data.eventType").value("PLACE_SAVED"))
			.andExpect(jsonPath("$.data.recordedAt").value("2026-07-19T04:00:01Z"));
	}

	@Test
	void rejectsServerOnlyUnknownAndIncompleteEvents() throws Exception {
		mockMvc.perform(post("/api/v1/recommendation-decks/{deckId}/events", DECK_PUBLIC_ID)
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"placeId":42,"eventType":"CARD_SERVED"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/api/v1/recommendation-decks/{deckId}/events", DECK_PUBLIC_ID)
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void hidesMissingForeignAndUnservedCards() throws Exception {
		when(eventService.recordEvent(
			USER_PUBLIC_ID,
			DECK_PUBLIC_ID,
			42L,
			RecommendationEventType.PLACE_SAVED,
			OCCURRED_AT))
			.thenThrow(new RecommendationDeckNotFoundException());

		mockMvc.perform(post("/api/v1/recommendation-decks/{deckId}/events", DECK_PUBLIC_ID)
				.with(user(USER_PUBLIC_ID).roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validBody()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_DECK_NOT_FOUND"));
	}

	private String validBody() {
		return """
			{"placeId":42,"eventType":"PLACE_SAVED","occurredAt":"2026-07-19T04:00:00Z"}
			""";
	}
}
