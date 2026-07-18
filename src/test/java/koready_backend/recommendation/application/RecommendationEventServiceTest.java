package koready_backend.recommendation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.recommendation.application.exception.RecommendationDeckNotFoundException;
import koready_backend.recommendation.application.port.RecommendationEventRepository;
import koready_backend.recommendation.application.port.RecommendationEventRepository.RecordEventCommand;
import koready_backend.recommendation.domain.RecommendationEventType;

@ExtendWith(MockitoExtension.class)
class RecommendationEventServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-19T04:05:06.654321Z");
	private static final Instant OCCURRED_AT = Instant.parse("2026-07-19T04:00:00.123456Z");
	private static final String USER_PUBLIC_ID = "usr_event_owner";
	private static final String DECK_PUBLIC_ID = "rec_event_deck";

	@Mock
	RecommendationEventRepository repository;

	private RecommendationEventService service;

	@BeforeEach
	void setUp() {
		service = new RecommendationEventService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void recordsTheClientOccurrenceAndServerReceiptTimesSeparately() {
		when(repository.record(any())).thenReturn(true);

		RecommendationEventService.RecommendationEvent result = service.recordEvent(
			USER_PUBLIC_ID,
			DECK_PUBLIC_ID,
			42L,
			RecommendationEventType.PLACE_DETAIL_CLICKED,
			OCCURRED_AT);

		ArgumentCaptor<RecordEventCommand> commandCaptor =
			ArgumentCaptor.forClass(RecordEventCommand.class);
		org.mockito.Mockito.verify(repository).record(commandCaptor.capture());
		RecordEventCommand command = commandCaptor.getValue();
		assertTrue(command.eventPublicId().startsWith("recevt_"));
		assertEquals(USER_PUBLIC_ID, command.userPublicId());
		assertEquals(DECK_PUBLIC_ID, command.deckPublicId());
		assertEquals(42L, command.placeId());
		assertEquals(RecommendationEventType.PLACE_DETAIL_CLICKED, command.eventType());
		assertEquals(OCCURRED_AT, command.occurredAt());
		assertEquals(NOW, command.recordedAt());
		assertEquals(command.eventPublicId(), result.eventId());
		assertEquals(NOW, result.recordedAt());
	}

	@Test
	void usesTheReceiptTimeWhenTheClientOmitsOccurredAt() {
		when(repository.record(any())).thenReturn(true);

		service.recordEvent(
			USER_PUBLIC_ID,
			DECK_PUBLIC_ID,
			42L,
			RecommendationEventType.CARD_NEXT,
			null);

		ArgumentCaptor<RecordEventCommand> commandCaptor =
			ArgumentCaptor.forClass(RecordEventCommand.class);
		org.mockito.Mockito.verify(repository).record(commandCaptor.capture());
		assertEquals(NOW, commandCaptor.getValue().occurredAt());
	}

	@Test
	void hidesMissingForeignAndUnservedCardsWithTheSameNotFoundError() {
		when(repository.record(any())).thenReturn(false);

		assertThrows(
			RecommendationDeckNotFoundException.class,
			() -> service.recordEvent(
				USER_PUBLIC_ID,
				DECK_PUBLIC_ID,
				42L,
				RecommendationEventType.ROUTE_OPENED,
				OCCURRED_AT));
	}
}
