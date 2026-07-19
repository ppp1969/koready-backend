package koready_backend.buddy.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.buddy.application.exception.BuddyUserUnavailableException;
import koready_backend.buddy.application.exception.InvalidMateCursorException;
import koready_backend.buddy.application.model.BuddyProfileView;
import koready_backend.buddy.application.port.BuddyMateRepository;
import koready_backend.buddy.application.port.BuddyMateRepository.MateCursor;
import koready_backend.buddy.application.port.BuddyMateRepository.MateQuery;
import koready_backend.buddy.application.port.BuddyMateRepository.MateRow;
import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.place.application.exception.PlaceNotFoundException;

@Service
public class BuddyMateService {

	private static final int MAX_PAGE_SIZE = 50;
	private static final int MAX_CURSOR_LENGTH = 512;

	private final BuddyProfileRepository profileRepository;
	private final BuddyMateRepository mateRepository;

	public BuddyMateService(
		BuddyProfileRepository profileRepository,
		BuddyMateRepository mateRepository
	) {
		this.profileRepository = profileRepository;
		this.mateRepository = mateRepository;
	}

	@Transactional(readOnly = true)
	public PlaceMatePage getMates(
		String requesterPublicId,
		long placeId,
		String cursorToken,
		int size
	) {
		validate(placeId, size);
		long requesterUserId = profileRepository.findActiveUserId(requesterPublicId)
			.orElseThrow(BuddyUserUnavailableException::new);
		if (!mateRepository.existsVisiblePlace(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}

		String fingerprint = fingerprint(
			"PLACE_MATES",
			Long.toString(requesterUserId),
			Long.toString(placeId));
		MateCursor cursor = decodeCursor(cursorToken, fingerprint);
		boolean requesterHasProfile =
			profileRepository.findByUserId(requesterUserId).isPresent();
		List<MateRow> rows = mateRepository.findAll(new MateQuery(
			requesterUserId, placeId, cursor, size + 1));
		boolean hasMore = rows.size() > size;
		List<MateRow> visibleRows = rows.subList(0, Math.min(size, rows.size()));
		List<BuddyProfileView> items = visibleRows.stream()
			.map(row -> BuddyProfileViews.forPublic(
				row.profile(),
				requesterHasProfile && row.profile().profile().allowsMessages(),
				false))
			.toList();

		String nextCursor = null;
		if (hasMore && !visibleRows.isEmpty()) {
			MateRow last = visibleRows.getLast();
			nextCursor = encodeCursor(
				fingerprint,
				new MateCursor(last.savedAt(), last.savedPlaceId()));
		}
		return new PlaceMatePage(placeId, items, nextCursor, hasMore);
	}

	private static void validate(long placeId, int size) {
		if (placeId <= 0) {
			throw new IllegalArgumentException("Place ID must be positive");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("Page size must be between 1 and 50");
		}
	}

	private static String fingerprint(String... values) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String value : values) {
				digest.update(Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest(), 0, 12);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static String encodeCursor(String fingerprint, MateCursor cursor) {
		String savedAt = cursor.savedAt().toString();
		String savedPlaceId = Long.toString(cursor.savedPlaceId());
		String checksum = cursorChecksum(fingerprint, savedAt, savedPlaceId);
		String payload = String.join("\t",
			"1",
			"PLACE_MATES",
			fingerprint,
			savedAt,
			savedPlaceId,
			checksum);
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static MateCursor decodeCursor(
		String token,
		String expectedFingerprint
	) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidMateCursorException();
		}
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = payload.split("\t", -1);
			if (parts.length != 6
				|| !"1".equals(parts[0])
				|| !"PLACE_MATES".equals(parts[1])
				|| !expectedFingerprint.equals(parts[2])
				|| !MessageDigest.isEqual(
					cursorChecksum(parts[2], parts[3], parts[4])
						.getBytes(StandardCharsets.US_ASCII),
					parts[5].getBytes(StandardCharsets.US_ASCII))) {
				throw new InvalidMateCursorException();
			}
			Instant savedAt = Instant.parse(parts[3]);
			long savedPlaceId = Long.parseLong(parts[4]);
			if (savedPlaceId <= 0) {
				throw new InvalidMateCursorException();
			}
			return new MateCursor(savedAt, savedPlaceId);
		} catch (InvalidMateCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidMateCursorException();
		}
	}

	private static String cursorChecksum(
		String fingerprint,
		String savedAt,
		String savedPlaceId
	) {
		return fingerprint("PLACE_MATES_CURSOR", fingerprint, savedAt, savedPlaceId);
	}

	public record PlaceMatePage(
		long placeId,
		List<BuddyProfileView> items,
		String nextCursor,
		boolean hasMore
	) {
		public PlaceMatePage {
			items = List.copyOf(items);
		}
	}
}
