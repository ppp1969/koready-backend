package koready_backend.terms.application;

public class AdminTermNotFoundException extends RuntimeException {
	public AdminTermNotFoundException() { super("Term or version was not found."); }
}
