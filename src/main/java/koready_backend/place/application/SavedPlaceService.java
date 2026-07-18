package koready_backend.place.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.place.application.exception.InvalidPlaceCursorException;
import koready_backend.place.application.exception.PlaceNotFoundException;
import koready_backend.place.application.exception.SavedPlaceUserUnavailableException;
import koready_backend.place.application.port.PlaceQueryRepository.FestivalOccurrence;
import koready_backend.place.application.port.SavedPlaceRepository;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceCriteria;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceCursor;
import koready_backend.place.application.port.SavedPlaceRepository.SavedPlaceRow;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.SavedPlaceSource;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@Service
public class SavedPlaceService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int MAX_CURSOR_LENGTH = 512;
	private static final int SHORT_DESCRIPTION_LENGTH = 160;

	private final SavedPlaceRepository repository;
	private final Clock clock;

	@Autowired
	public SavedPlaceService(SavedPlaceRepository repository) {
		this(repository, Clock.system(SEOUL_ZONE));
	}

	SavedPlaceService(SavedPlaceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public SaveResult save(String userPublicId, long placeId, SavedPlaceSource source) {
		long userId = activeUserId(userPublicId);
		if (!repository.existsVisiblePlace(placeId)) {
			throw new PlaceNotFoundException(placeId);
		}
		var saved = repository.save(userId, placeId, source, clock.instant());
		return new SaveResult(saved.placeId(), true, saved.savedAt());
	}

	@Transactional
	public void unsave(String userPublicId, long placeId) {
		repository.unsave(activeUserId(userPublicId), placeId, clock.instant());
	}

	@Transactional(readOnly = true)
	public SavedPlacePage getSavedPlaces(
		String userPublicId,
		String cursorToken,
		int size,
		PlaceLanguage language
	) {
		long userId = activeUserId(userPublicId);
		String fingerprint = fingerprint("SAVED_PLACES", language.name());
		SavedPlaceCursor cursor = decodeCursor(cursorToken, fingerprint);
		LocalDate today = LocalDate.now(clock);
		List<SavedPlaceRow> rows = repository.findAll(new SavedPlaceCriteria(
			userId, cursor, size + 1, language, today));
		boolean hasMore = rows.size() > size;
		List<SavedPlaceRow> visibleRows = rows.subList(0, Math.min(size, rows.size()));
		List<SavedPlaceCard> items = visibleRows.stream()
			.map(row -> card(row, language, today))
			.toList();

		String nextCursor = null;
		if (hasMore && !visibleRows.isEmpty()) {
			SavedPlaceRow last = visibleRows.getLast();
			nextCursor = encodeCursor(
				fingerprint,
				new SavedPlaceCursor(last.savedAt(), last.savedPlaceId()));
		}
		return new SavedPlacePage(items, nextCursor, hasMore);
	}

	private long activeUserId(String userPublicId) {
		return repository.findActiveUserId(userPublicId)
			.orElseThrow(SavedPlaceUserUnavailableException::new);
	}

	private static SavedPlaceCard card(
		SavedPlaceRow row,
		PlaceLanguage language,
		LocalDate today
	) {
		return new SavedPlaceCard(
			row.placeId(),
			row.title(),
			row.serviceRegionCode(),
			row.serviceRegionName(),
			row.addressSummary(),
			row.imageUrl(),
			occurrence(row.festivalOccurrence(), language, today),
			row.travelStyle(),
			List.of(),
			shortDescription(row.overview()),
			true,
			row.savedAt());
	}

	private static FestivalOccurrenceSummary occurrence(
		FestivalOccurrence occurrence,
		PlaceLanguage language,
		LocalDate today
	) {
		if (occurrence == null) {
			return null;
		}
		String status;
		if (today.isBefore(occurrence.startDate())) {
			status = "UPCOMING";
		} else if (today.isAfter(occurrence.endDate())) {
			status = "ENDED";
		} else {
			status = "ONGOING";
		}
		DateTimeFormatter formatter = language == PlaceLanguage.EN
			? DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
			: DateTimeFormatter.ofPattern("M.d");
		return new FestivalOccurrenceSummary(
			occurrence.occurrenceId(),
			occurrence.eventYear(),
			occurrence.startDate(),
			occurrence.endDate(),
			status,
			formatter.format(occurrence.startDate())
				+ " - " + formatter.format(occurrence.endDate()));
	}

	private static String shortDescription(String overview) {
		if (overview == null || overview.isBlank()) {
			return null;
		}
		String normalized = overview.strip().replaceAll("\\s+", " ");
		if (normalized.length() <= SHORT_DESCRIPTION_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, SHORT_DESCRIPTION_LENGTH - 3).stripTrailing() + "...";
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

	private static String encodeCursor(String fingerprint, SavedPlaceCursor cursor) {
		String payload = String.join("\t",
			"1", fingerprint, cursor.savedAt().toString(),
			Long.toString(cursor.savedPlaceId()));
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static SavedPlaceCursor decodeCursor(String token, String expectedFingerprint) {
		if (token == null || token.isBlank()) {
			return null;
		}
		if (token.length() > MAX_CURSOR_LENGTH) {
			throw new InvalidPlaceCursorException();
		}
		try {
			String payload = new String(
				Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			String[] parts = payload.split("\t", -1);
			if (parts.length != 4
				|| !"1".equals(parts[0])
				|| !expectedFingerprint.equals(parts[1])) {
				throw new InvalidPlaceCursorException();
			}
			Instant savedAt = Instant.parse(parts[2]);
			long savedPlaceId = Long.parseLong(parts[3]);
			if (savedPlaceId <= 0) {
				throw new InvalidPlaceCursorException();
			}
			return new SavedPlaceCursor(savedAt, savedPlaceId);
		} catch (InvalidPlaceCursorException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new InvalidPlaceCursorException();
		}
	}

	public record SaveResult(long placeId, boolean saved, Instant savedAt) {
	}

	public record SavedPlacePage(
		List<SavedPlaceCard> items,
		String nextCursor,
		boolean hasMore
	) {
	}

	public record SavedPlaceCard(
		long placeId,
		String title,
		ServiceRegionCode serviceRegionCode,
		String serviceRegionName,
		String addressSummary,
		String imageUrl,
		FestivalOccurrenceSummary festivalOccurrence,
		TravelStyle travelStyle,
		List<String> tags,
		String shortDescription,
		boolean saved,
		Instant savedAt
	) {
	}

	public record FestivalOccurrenceSummary(
		long occurrenceId,
		int eventYear,
		LocalDate startDate,
		LocalDate endDate,
		String status,
		String dateRangeText
	) {
	}
}
