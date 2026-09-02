package koready_backend.terms.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import koready_backend.terms.application.port.AdminTermsRepository;
import koready_backend.terms.application.port.AdminTermsRepository.TermVersion;

class AdminTermsServiceTest {
	private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
	private final AdminTermsRepository repository = mock(AdminTermsRepository.class);
	private final AdminTermsService service = new AdminTermsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void publishesADraftWithContentUrl() {
		var version = new TermVersion(3, 1, "1.0", "개인정보처리방침", URI.create("https://koready.cloud/privacy"), true,
			Instant.parse("2026-09-10T00:00:00Z"), null, null);
		when(repository.findVersion(1, 3)).thenReturn(Optional.of(version));
		when(repository.publish(1, 3, NOW)).thenReturn(Optional.of(version));

		service.publish(1, 3);

		verify(repository).publish(1, 3, NOW);
	}

	@Test
	void rejectsPublishingWithoutContent() {
		when(repository.findVersion(1, 3)).thenReturn(Optional.of(new TermVersion(
			3, 1, "1.0", "약관", null, true, NOW, null, null)));
		assertThrows(AdminTermConflictException.class, () -> service.publish(1, 3));
	}

	@Test
	void normalizesDefinitionCodes() {
		when(repository.createDefinition("AGE_OVER_14", 3, true, NOW))
			.thenReturn(new AdminTermsRepository.TermDefinition(4, "AGE_OVER_14", 3, true, java.util.List.of()));
		assertEquals("AGE_OVER_14", service.createDefinition(" age_over_14 ", 3, true).code());
	}
}
