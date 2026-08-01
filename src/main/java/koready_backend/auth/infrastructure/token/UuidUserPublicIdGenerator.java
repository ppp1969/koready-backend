package koready_backend.auth.infrastructure.token;

import java.util.UUID;

import koready_backend.auth.application.port.UserPublicIdGenerator;

public final class UuidUserPublicIdGenerator implements UserPublicIdGenerator {

	@Override
	public String newUserPublicId() {
		return "usr_" + UUID.randomUUID().toString().replace("-", "");
	}
}
