package koready_backend.auth.application.port;

import koready_backend.auth.domain.GoogleIdentity;

public interface GoogleIdentityVerifier {

	GoogleIdentity verify(String idToken);
}
