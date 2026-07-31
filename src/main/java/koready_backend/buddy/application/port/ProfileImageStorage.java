package koready_backend.buddy.application.port;

import java.time.Instant;
import java.util.Map;

public interface ProfileImageStorage {

	UploadTarget createUpload(String objectKey, String contentType, Instant now);

	StoredObject inspect(String objectKey);

	void delete(String objectKey);

	String createViewUrl(String objectKey, Instant now);

	record UploadTarget(
		String uploadUrl,
		Instant expiresAt,
		Map<String, String> requiredHeaders
	) {
	}

	record StoredObject(
		String contentType,
		long contentLength,
		byte[] signature
	) {
		public StoredObject {
			signature = signature.clone();
		}

		@Override
		public byte[] signature() {
			return signature.clone();
		}
	}
}
