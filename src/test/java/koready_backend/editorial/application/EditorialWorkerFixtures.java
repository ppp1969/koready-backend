package koready_backend.editorial.application;

import java.time.Instant;
import java.util.List;

import koready_backend.editorial.application.port.EditorialWorkerRepository.ClaimedJob;
import koready_backend.editorial.application.port.EditorialWorkerRepository.GenerationSource;

final class EditorialWorkerFixtures {

	private EditorialWorkerFixtures() {
	}

	static ClaimedJob claimed(String leaseToken, int attemptCount) {
		return new ClaimedJob(
			1L, "job-1", 10L, "fingerprint", "prompt-v1", leaseToken,
			attemptCount, new GenerationSource(
				10L, "한국어 제목", "English title", "서울 종로구",
				"사실 기반의 충분한 한국어 원문 설명입니다.",
				List.of("CULTURE_EXPERIENCE"), List.of()),
			Instant.parse("2026-08-13T00:00:00Z"));
	}
}
