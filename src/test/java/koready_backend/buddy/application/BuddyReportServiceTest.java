package koready_backend.buddy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.ReportIdempotencyConflictException;
import koready_backend.buddy.application.exception.ReportNotAllowedException;
import koready_backend.buddy.application.exception.ReportTargetNotFoundException;
import koready_backend.buddy.application.port.BuddyReportRepository;
import koready_backend.buddy.application.port.BuddyReportRepository.MessageTarget;
import koready_backend.buddy.application.port.BuddyReportRepository.NewReport;
import koready_backend.buddy.application.port.BuddyReportRepository.ProfileTarget;
import koready_backend.buddy.application.port.BuddyReportRepository.StoredReport;
import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

@ExtendWith(MockitoExtension.class)
class BuddyReportServiceTest {

	private static final String KEY = "report-key-001";
	private static final Instant NOW = Instant.parse("2026-07-19T09:00:00Z");

	@Mock
	private BuddyReportRepository repository;

	private BuddyReportService service;

	@BeforeEach
	void setUp() {
		service = new BuddyReportService(
			repository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsAndReplaysTheSameProfileReport() {
		when(repository.findActiveReporterUserId("usr_reporter"))
			.thenReturn(Optional.of(10L));
		when(repository.findByIdempotencyKey(10L, KEY)).thenReturn(Optional.empty());
		when(repository.findActiveProfileTarget(51L))
			.thenReturn(Optional.of(new ProfileTarget(51L, 11L)));
		AtomicReference<StoredReport> stored = new AtomicReference<>();
		when(repository.save(any())).thenAnswer(invocation -> {
			NewReport report = invocation.getArgument(0);
			assertThat(report.reporterUserId()).isEqualTo(10L);
			assertThat(report.targetType()).isEqualTo(ReportTargetType.PROFILE);
			assertThat(report.targetProfileId()).isEqualTo(51L);
			assertThat(report.targetMessageId()).isNull();
			assertThat(report.reason()).isEqualTo("Impersonation");
			StoredReport result = new StoredReport(
				9001L,
				ReportTargetType.PROFILE,
				"51",
				ReportStatus.RECEIVED,
				NOW,
				report.requestHash());
			stored.set(result);
			return result;
		});

		BuddyReportService.CreateReportCommand command =
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "51", "  Impersonation  ");
		BuddyReportService.ReportResult first = service.create(
			"usr_reporter", KEY, command);
		when(repository.findByIdempotencyKey(10L, KEY))
			.thenReturn(Optional.of(stored.get()));
		BuddyReportService.ReportResult replay = service.create(
			"usr_reporter", KEY, command);

		assertThat(first).isEqualTo(replay);
		assertThat(first.reportId()).isEqualTo(9001L);
		assertThat(first.status()).isEqualTo(ReportStatus.RECEIVED);
		verify(repository, times(1)).findActiveProfileTarget(51L);
		verify(repository, times(1)).save(any());
	}

	@Test
	void createsAReportOnlyForAMessageReceivedByTheReporter() {
		when(repository.findActiveReporterUserId("usr_receiver"))
			.thenReturn(Optional.of(10L));
		when(repository.findByIdempotencyKey(10L, KEY)).thenReturn(Optional.empty());
		when(repository.findReceivedMessageTarget(7001L, 10L))
			.thenReturn(Optional.of(new MessageTarget(7001L, 51L)));
		when(repository.save(any())).thenAnswer(invocation -> {
			NewReport report = invocation.getArgument(0);
			assertThat(report.targetProfileId()).isEqualTo(51L);
			assertThat(report.targetMessageId()).isEqualTo(7001L);
			return new StoredReport(
				9002L,
				ReportTargetType.MESSAGE,
				"7001",
				ReportStatus.RECEIVED,
				NOW,
				report.requestHash());
		});

		BuddyReportService.ReportResult result = service.create(
			"usr_receiver",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.MESSAGE, "7001", "Abusive language"));

		assertThat(result.targetType()).isEqualTo(ReportTargetType.MESSAGE);
		assertThat(result.targetId()).isEqualTo("7001");
	}

	@Test
	void rejectsSelfProfileReportsAndForeignMessages() {
		when(repository.findActiveReporterUserId("usr_reporter"))
			.thenReturn(Optional.of(10L));
		when(repository.findByIdempotencyKey(10L, KEY)).thenReturn(Optional.empty());
		when(repository.findActiveProfileTarget(50L))
			.thenReturn(Optional.of(new ProfileTarget(50L, 10L)));
		when(repository.findReceivedMessageTarget(7002L, 10L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(
			"usr_reporter",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "50", "Self")))
			.isInstanceOf(ReportNotAllowedException.class);
		assertThatThrownBy(() -> service.create(
			"usr_reporter",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.MESSAGE, "7002", "Foreign")))
			.isInstanceOf(ReportTargetNotFoundException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void rejectsAnIdempotencyKeyReusedForAnotherRequestBeforeTargetLookup() {
		when(repository.findActiveReporterUserId("usr_reporter"))
			.thenReturn(Optional.of(10L));
		when(repository.findByIdempotencyKey(10L, KEY)).thenReturn(Optional.of(
			new StoredReport(
				9001L,
				ReportTargetType.PROFILE,
				"51",
				ReportStatus.RECEIVED,
				NOW,
				"f".repeat(64))));

		assertThatThrownBy(() -> service.create(
			"usr_reporter",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "52", "Different")))
			.isInstanceOf(ReportIdempotencyConflictException.class);
		verify(repository, never()).findActiveProfileTarget(52L);
	}

	@Test
	void validatesPrincipalKeyTargetAndUnicodeReasonBoundaries() {
		when(repository.findActiveReporterUserId("usr_missing"))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(
			"usr_missing",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "51", "Reason")))
			.isInstanceOf(BuddyUserUnavailableException.class);
		assertThatThrownBy(() -> service.create(
			"usr_reporter",
			"short",
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "51", "Reason")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create(
			"usr_reporter",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "051", "Reason")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.create(
			"usr_reporter",
			KEY,
			new BuddyReportService.CreateReportCommand(
				ReportTargetType.PROFILE, "51", "🙂".repeat(501))))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
