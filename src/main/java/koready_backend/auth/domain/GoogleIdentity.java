package koready_backend.auth.domain;

public record GoogleIdentity(
	String providerSubject,
	String email
) {

	public GoogleIdentity {
		if (providerSubject == null || providerSubject.isBlank()) {
			throw new IllegalArgumentException("Google subject is required.");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Verified Google email is required.");
		}
	}
}
