package koready_backend.account.infrastructure.storage;

import org.springframework.stereotype.Component;

import koready_backend.account.application.AccountAssetDeletionPort;
import koready_backend.buddy.application.port.ProfileImageStorage;

@Component
public class ProfileImageAccountAssetDeletionAdapter implements AccountAssetDeletionPort {

	private final ProfileImageStorage storage;

	public ProfileImageAccountAssetDeletionAdapter(ProfileImageStorage storage) {
		this.storage = storage;
	}

	@Override
	public void delete(String objectKey) {
		storage.delete(objectKey);
	}
}
