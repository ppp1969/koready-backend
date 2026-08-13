package koready_backend.editorial.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import koready_backend.editorial.application.port.EditorialRepository;
import koready_backend.editorial.application.port.EditorialRepository.EnqueueRecord;
import koready_backend.editorial.domain.EditorialJobPriority;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.EditorialTriggerType;
import koready_backend.editorial.domain.EditorialLanguage;

class EditorialServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
	private final EditorialRepository repository = Mockito.mock(EditorialRepository.class);
	private final EditorialService service = new EditorialService(
		repository,
		new EditorialProperties("koready-place-editorial-v1", false),
		Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void pmSelectionQueuesHighPriorityWork() {
		when(repository.enqueue(Mockito.any())).thenReturn(new EnqueueRecord(
			"job-1", 10L, EditorialJobStatus.QUEUED,
			EditorialJobPriority.HIGH, EditorialTriggerType.PM_CURATED, NOW, true));

		EditorialService.JobView result = service.enqueueByAdmin(10L, "admin-subject");

		assertEquals(EditorialJobPriority.HIGH, result.priority());
		assertEquals(EditorialTriggerType.PM_CURATED, result.triggerType());
		verify(repository).enqueue(Mockito.argThat(command ->
			command.placeId() == 10L
				&& command.priority() == EditorialJobPriority.HIGH
				&& command.triggerType() == EditorialTriggerType.PM_CURATED));
	}

	@Test
	void userDetailQueuesNormalPriorityAndReturnsPendingState() {
		when(repository.findReady(20L, EditorialLanguage.KO, "koready-place-editorial-v1"))
			.thenReturn(Optional.empty());
		when(repository.enqueue(Mockito.any())).thenReturn(new EnqueueRecord(
			"job-2", 20L, EditorialJobStatus.QUEUED,
			EditorialJobPriority.NORMAL, EditorialTriggerType.USER_DETAIL, NOW, false));

		EditorialService.PublicEditorial result = service.findOrEnqueue(
			20L, EditorialLanguage.KO, null);

		assertEquals(EditorialJobStatus.QUEUED, result.status());
		assertFalse(result.ready());
		assertEquals(null, result.content());
	}
}
