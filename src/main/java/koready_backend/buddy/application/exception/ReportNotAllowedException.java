package koready_backend.buddy.application.exception;

public class ReportNotAllowedException extends RuntimeException {

	public ReportNotAllowedException() {
		super("The report is not allowed for this target.");
	}
}
