package koready_backend.editorial.application;

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

		assertTrue(worker.processNext());

		verify(repository).fail(Mockito.argThat(command ->
			command.retry()
				&& command.errorCode().equals("AI_GENERATION_FAILED")
				&& !command.errorMessage().contains("secret prompt")));
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
}
