package koready_backend.buddy.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.ProfileImageRepository;
import koready_backend.buddy.application.port.ProfileImageRepository.ImageRecord;
import koready_backend.buddy.application.port.ProfileImageRepository.ImageStatus;
import koready_backend.buddy.application.port.ProfileImageStorage;

@Service
public class ProfileImageService {

	public static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final Map<String, String> EXTENSIONS = Map.of(
		"image/jpeg", "jpg",
		"image/png", "png",
		"image/webp", "webp");

	private final BuddyProfileRepository profiles;
	private final ProfileImageRepository images;
	private final ProfileImageStorage storage;
	private final Clock clock;
	private final Supplier<UUID> uuids;

	@Autowired
	public ProfileImageService(
		BuddyProfileRepository profiles,
		ProfileImageRepository images,
		ProfileImageStorage storage
	) {
		this(profiles, images, storage, Clock.system(SEOUL_ZONE), UUID::randomUUID);
	}

	ProfileImageService(
		BuddyProfileRepository profiles,
		ProfileImageRepository images,
		ProfileImageStorage storage,
		Clock clock,
		Supplier<UUID> uuids
	) {
		this.profiles = profiles;
		this.images = images;
		this.storage = storage;
		this.clock = clock;
		this.uuids = uuids;
	}

	@Transactional
	public UploadReservation reserve(
		String userPublicId,
		String requestedContentType,
		long declaredSize
	) {
		long userId = activeUser(userPublicId);
		String contentType = normalizeContentType(requestedContentType);
		validateSize(declaredSize);
		UUID uuid = uuids.get();
		String imageId = "img_" + uuid.toString().replace("-", "");
		String objectKey = "profile-images/" + userPublicId + "/" + uuid
			+ "." + EXTENSIONS.get(contentType);
		Instant now = clock.instant();
		var target = storage.createUpload(objectKey, contentType, now);
		images.savePending(new ImageRecord(
			imageId,
			userId,
			objectKey,
			contentType,
			declaredSize,
			null,
			ImageStatus.PENDING,
			now,
			null));
		return new UploadReservation(
			imageId,
			target.uploadUrl(),
			target.expiresAt(),
			target.requiredHeaders());
	}

	@Transactional
	public CompletedImage complete(String userPublicId, String imageId) {
		long userId = activeUser(userPublicId);
		ImageRecord image = images.findOwned(imageId, userId)
			.filter(record -> record.status() == ImageStatus.PENDING)
			.orElseThrow(() -> new IllegalArgumentException(
				"Profile image reservation is unavailable"));
		var stored = storage.inspect(image.objectKey());
		if (!image.contentType().equals(normalizeContentType(stored.contentType()))
			|| !matchesSignature(image.contentType(), stored.signature())
			|| stored.contentLength() <= 0
			|| stored.contentLength() > image.declaredSize()
			|| stored.contentLength() > MAX_IMAGE_BYTES) {
			storage.delete(image.objectKey());
			throw new IllegalArgumentException(
				"Uploaded profile image does not match its reservation");
		}
		Instant now = clock.instant();
		images.markReady(image.imageId(), stored.contentLength(), now);
		return new CompletedImage(
			image.imageId(), publicPath(image.imageId()), stored.contentLength(), now);
	}

	@Transactional(readOnly = true)
	public Optional<String> viewUrl(String imageId, String viewerPublicId) {
		return images.findViewable(imageId, viewerPublicId)
			.map(image -> storage.createViewUrl(image.objectKey(), clock.instant()));
	}

	@Transactional(readOnly = true)
	public boolean isReadyOwnedBy(long userId, String profileImageUrl) {
		String imageId = imageId(profileImageUrl);
		return images.findOwned(imageId, userId)
			.map(image -> image.status() == ImageStatus.READY)
			.orElse(false);
	}

	public static String publicPath(String imageId) {
		return "/api/v1/profile-images/" + imageId;
	}

	private static String imageId(String profileImageUrl) {
		if (profileImageUrl == null
			|| !profileImageUrl.startsWith("/api/v1/profile-images/")) {
			throw new IllegalArgumentException(
				"Profile image URL must come from a completed upload");
		}
		String imageId = profileImageUrl.substring(
			"/api/v1/profile-images/".length());
		if (!imageId.matches("img_[0-9a-f]{32}")) {
			throw new IllegalArgumentException(
				"Profile image URL must come from a completed upload");
		}
		return imageId;
	}

	private long activeUser(String publicId) {
		return profiles.findActiveUserId(publicId)
			.orElseThrow(BuddyUserUnavailableException::new);
	}

	private static String normalizeContentType(String value) {
		String normalized = value == null
			? ""
			: value.trim().toLowerCase(Locale.ROOT);
		if (!EXTENSIONS.containsKey(normalized)) {
			throw new IllegalArgumentException(
				"Profile image content type must be JPEG, PNG, or WebP");
		}
		return normalized;
	}

	private static void validateSize(long size) {
		if (size <= 0 || size > MAX_IMAGE_BYTES) {
			throw new IllegalArgumentException(
				"Profile image size must be between 1 byte and 5 MiB");
		}
	}

	private static boolean matchesSignature(String contentType, byte[] bytes) {
		return switch (contentType) {
			case "image/jpeg" -> startsWith(
				bytes, 0xff, 0xd8, 0xff);
			case "image/png" -> startsWith(
				bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
			case "image/webp" -> startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
				&& matchesAt(bytes, 8, 0x57, 0x45, 0x42, 0x50);
			default -> false;
		};
	}

	private static boolean startsWith(byte[] bytes, int... expected) {
		return matchesAt(bytes, 0, expected);
	}

	private static boolean matchesAt(
		byte[] bytes,
		int offset,
		int... expected
	) {
		if (bytes.length < offset + expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if (Byte.toUnsignedInt(bytes[offset + index]) != expected[index]) {
				return false;
			}
		}
		return true;
	}

	public record UploadReservation(
		String imageId,
		String uploadUrl,
		Instant expiresAt,
		Map<String, String> requiredHeaders
	) {
	}

	public record CompletedImage(
		String imageId,
		String profileImageUrl,
		long size,
		Instant completedAt
	) {
	}
}
