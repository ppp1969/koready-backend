package koready_backend.buddy.controller;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import koready_backend.buddy.application.BuddyReportService;
import koready_backend.buddy.domain.ReportStatus;
import koready_backend.buddy.domain.ReportTargetType;

final class BuddyReportDtos {

	private BuddyReportDtos() {
	}

	record ReportRequest(
		@NotNull ReportTargetType targetType,
		@NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String targetId,
		@NotNull String reason
	) {
		BuddyReportService.CreateReportCommand toCommand() {
			return new BuddyReportService.CreateReportCommand(
				targetType, targetId, reason);
		}
	}

	record ReportResponse(
		long reportId,
		ReportTargetType targetType,
		String targetId,
		ReportStatus status,
		Instant createdAt
	) {
		static ReportResponse from(BuddyReportService.ReportResult result) {
			return new ReportResponse(
				result.reportId(),
				result.targetType(),
				result.targetId(),
				result.status(),
				result.createdAt());
		}
	}
}
