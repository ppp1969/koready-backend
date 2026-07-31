package koready_backend.buddy.application.exception;

public class ProfileImageStorageUnavailableException extends RuntimeException {

	public ProfileImageStorageUnavailableException() {
		super("Profile image storage is unavailable");
	}

	public ProfileImageStorageUnavailableException(Throwable cause) {
		super("Profile image storage is unavailable", cause);
	}
}
