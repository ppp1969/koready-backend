package koready_backend.kto.application.exception;

public class KtoRelatedTourNotFoundException
	extends RuntimeException {

	public KtoRelatedTourNotFoundException() {
		super("Related tour record was not found");
	}
}
