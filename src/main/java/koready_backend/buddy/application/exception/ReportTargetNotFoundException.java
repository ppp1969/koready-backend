package koready_backend.buddy.application.exception;

public class ReportTargetNotFoundException extends RuntimeException {

	public ReportTargetNotFoundException() {
		super("Report target not found.");
	}
}
