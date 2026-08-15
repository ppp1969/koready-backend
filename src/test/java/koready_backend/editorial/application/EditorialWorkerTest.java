package koready_backend.editorial.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.genai.errors.ClientException;

import koready_backend.editorial.application.port.EditorialGenerator;
import koready_backend.editorial.application.port.EditorialWorkerRepository;
import koready_backend.editorial.domain.EditorialGeneration;
import koready_backend.editorial.domain.EditorialGeneration.LocalizedContent;
import koready_backend.editorial.domain.TourismPurposeTag;

class EditorialWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
	private final EditorialWorkerRepository repository = Mockito.mock(EditorialWorkerRepository.class);
	private final EditorialGenerator generator = Mockito.mock(EditorialGenerator.class);
	private final EditorialWorkerProperties properties = new EditorialWorkerProperties(
		true, Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(10), 2, 100);
	private final EditorialWorker worker = new EditorialWorker(
		repository, generator, new EditorialOutputValidator(), properties,
		Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void isolatesStartupLeaseRecoveryFailure() {
		Mockito.doThrow(new IllegalStateException("database unavailable"))
			.when(repository).recoverExpiredLeases(Mockito.any(), Mockito.anyInt());

		assertDoesNotThrow(worker::recoverOnStartup);
	}

	@Test
	void claimsGeneratesAndCompletesOneJob() {
		var claimed = EditorialWorkerFixtures.claimed("lease-1", 1);
		when(repository.countStartedBetween(Mockito.any(), Mockito.any())).thenReturn(0L);
		when(repository.claimNext(Mockito.any())).thenReturn(Optional.of(claimed));
		when(generator.generate(claimed.source())).thenReturn(validGeneration());

		assertTrue(worker.processNext());

		verify(repository).complete(Mockito.argThat(command ->
			command.jobId() == claimed.jobId()
				&& command.leaseToken().equals("lease-1")
				&& command.generation().tags().size() == 2));
	}

	@Test
	void retriesSafeFailureWithoutLeakingPromptData() {
		var claimed = EditorialWorkerFixtures.claimed("lease-2", 1);
		when(repository.countStartedBetween(Mockito.any(), Mockito.any())).thenReturn(0L);
		when(repository.claimNext(Mockito.any())).thenReturn(Optional.of(claimed));
		when(generator.generate(claimed.source())).thenThrow(new IllegalStateException("secret prompt"));
		ListAppender<ILoggingEvent> logs = captureWorkerLogs();

		assertTrue(worker.processNext());

		verify(repository).fail(Mockito.argThat(command ->
			command.retry()
				&& command.errorCode().equals("AI_GENERATION_FAILED")
				&& !command.errorMessage().contains("secret prompt")));
		assertEquals(1, logs.list.size());
		String message = logs.list.getFirst().getFormattedMessage();
		assertTrue(message.contains("jobId=1"));
		assertTrue(message.contains("jobPublicId=job-1"));
		assertTrue(message.contains("placeId=10"));
		assertTrue(message.contains("attempt=1"));
		assertTrue(message.contains("errorCode=AI_GENERATION_FAILED"));
		assertTrue(message.contains("errorCategory=UNEXPECTED"));
		assertTrue(message.contains("exceptionType=IllegalStateException"));
		assertTrue(message.contains("providerHttpStatus=null"));
		assertTrue(message.contains("retry=true"));
		assertTrue(message.contains("nextAttemptAt=2026-08-13T00:10:00Z"));
		assertFalse(message.contains("secret prompt"));
		assertEquals(null, logs.list.getFirst().getThrowableProxy());
	}

	@Test
	void classifiesProviderHttpStatusWithoutLoggingProviderMessage() {
		var claimed = EditorialWorkerFixtures.claimed("lease-3", 1);
		when(repository.countStartedBetween(Mockito.any(), Mockito.any())).thenReturn(0L);
		when(repository.claimNext(Mockito.any())).thenReturn(Optional.of(claimed));
		when(generator.generate(claimed.source())).thenThrow(
			new ClientException(429, "RESOURCE_EXHAUSTED", "secret provider response"));
		ListAppender<ILoggingEvent> logs = captureWorkerLogs();

		assertTrue(worker.processNext());

		String message = logs.list.getFirst().getFormattedMessage();
		assertTrue(message.contains("errorCategory=PROVIDER_RATE_LIMIT"));
		assertTrue(message.contains("providerHttpStatus=429"));
		assertFalse(message.contains("secret provider response"));
		assertEquals(null, logs.list.getFirst().getThrowableProxy());
	}

	@Test
	void doesNotClaimWhenDailyLimitIsReached() {
		when(repository.countStartedBetween(Mockito.any(), Mockito.any())).thenReturn(100L);

		assertFalse(worker.processNext());

		verify(repository, Mockito.never()).claimNext(Mockito.any());
	}

	private static EditorialGeneration validGeneration() {
		return new EditorialGeneration(
			new LocalizedContent(
				"김천에서 즐기는 맛있는 한 줄 여행",
				"김밥을 주제로 먹고 만들며 즐기는 지역 축제예요.",
				"다양한 김밥과 지역 먹거리를 만날 수 있어요. 김천의 축제 분위기도 함께 즐겨보세요.",
				List.of("김밥 부스에서 맛보기", "김밥 만들기 체험하기", "포토존에서 사진 찍기")),
			new LocalizedContent(
				"A Flavorful Gimbap Journey in Gimcheon",
				"A local festival where you can taste and make gimbap.",
				"Explore different kinds of gimbap and local food. Enjoy the lively festival atmosphere in Gimcheon.",
				List.of("Taste gimbap at food booths", "Join a gimbap-making activity", "Take photos at the festival zone")),
			List.of(TourismPurposeTag.FOOD, TourismPurposeTag.EXPERIENCE),
			"openai", "test-model", 100, 200);
	}

	private static ListAppender<ILoggingEvent> captureWorkerLogs() {
		Logger logger = (Logger) LoggerFactory.getLogger(EditorialWorker.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return appender;
	}
}
