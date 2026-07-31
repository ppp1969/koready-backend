package koready_backend.buddy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import koready_backend.buddy.application.port.BuddyProfileRepository;
import koready_backend.buddy.application.port.ProfileImageRepository;
import koready_backend.buddy.application.port.ProfileImageRepository.ImageRecord;
import koready_backend.buddy.application.port.ProfileImageRepository.ImageStatus;
import koready_backend.buddy.application.port.ProfileImageStorage;
import koready_backend.buddy.application.port.ProfileImageStorage.StoredObject;
import koready_backend.buddy.application.port.ProfileImageStorage.UploadTarget;

class ProfileImageServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final UUID UUID_VALUE =
		UUID.fromString("11111111-2222-3333-4444-555555555555");

	private final BuddyProfileRepository profiles = mock(BuddyProfileRepository.class);
	private final ProfileImageRepository images = mock(ProfileImageRepository.class);
	private final ProfileImageStorage storage = mock(ProfileImageStorage.class);
	private final ProfileImageService service =
		new ProfileImageService(profiles, images, storage, CLOCK, () -> UUID_VALUE);

	@Test
	void createsABoundedOwnedUploadReservation() {
		when(profiles.findActiveUserId("usr_emma")).thenReturn(Optional.of(7L));
		when(storage.createUpload(any(), eq("image/jpeg"), eq(NOW)))
			.thenReturn(new UploadTarget(
				"https://signed.example/upload",
				NOW.plusSeconds(600),
				Map.of("Content-Type", "image/jpeg")));

		ProfileImageService.UploadReservation result =
			service.reserve("usr_emma", "image/jpeg", 1_024L);

		assertEquals("img_11111111222233334444555555555555", result.imageId());
		assertEquals("https://signed.example/upload", result.uploadUrl());
		verify(images).savePending(new ImageRecord(
			"img_11111111222233334444555555555555",
			7L,
			"profile-images/usr_emma/11111111-2222-3333-4444-555555555555.jpg",
			"image/jpeg",
			1_024L,
			null,
			ImageStatus.PENDING,
			NOW,
			null));
	}

	@Test
	void rejectsUnsupportedOrOversizedUploadsBeforeCallingStorage() {
		when(profiles.findActiveUserId("usr_emma")).thenReturn(Optional.of(7L));

		assertThrows(IllegalArgumentException.class,
			() -> service.reserve("usr_emma", "image/gif", 1_024L));
		assertThrows(IllegalArgumentException.class,
			() -> service.reserve("usr_emma", "image/png", 5_242_881L));
		assertThrows(IllegalArgumentException.class,
			() -> service.reserve("usr_emma", "image/png", 0L));
	}

	@Test
	void completesOnlyAnOwnedObjectWhoseActualMetadataMatches() {
		ImageRecord pending = pending();
		when(profiles.findActiveUserId("usr_emma")).thenReturn(Optional.of(7L));
		when(images.findOwned("img_11111111222233334444555555555555", 7L))
			.thenReturn(Optional.of(pending));
		when(storage.inspect(pending.objectKey()))
			.thenReturn(new StoredObject(
				"image/jpeg",
				1_000L,
				new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));

		ProfileImageService.CompletedImage result =
			service.complete("usr_emma", pending.imageId());

		assertEquals(
			"/api/v1/profile-images/img_11111111222233334444555555555555",
			result.profileImageUrl());
		verify(images).markReady(pending.imageId(), 1_000L, NOW);
	}

	@Test
	void deletesAnObjectThatDoesNotMatchTheReservation() {
		ImageRecord pending = pending();
		when(profiles.findActiveUserId("usr_emma")).thenReturn(Optional.of(7L));
		when(images.findOwned(pending.imageId(), 7L)).thenReturn(Optional.of(pending));
		when(storage.inspect(pending.objectKey()))
			.thenReturn(new StoredObject(
				"image/png",
				6_000_000L,
				new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}));

		assertThrows(IllegalArgumentException.class,
			() -> service.complete("usr_emma", pending.imageId()));
		verify(storage).delete(pending.objectKey());
	}

	@Test
	void rejectsAFileWhoseBytesDoNotMatchItsImageMetadata() {
		ImageRecord pending = pending();
		when(profiles.findActiveUserId("usr_emma")).thenReturn(Optional.of(7L));
		when(images.findOwned(pending.imageId(), 7L)).thenReturn(Optional.of(pending));
		when(storage.inspect(pending.objectKey()))
			.thenReturn(new StoredObject(
				"image/jpeg",
				1_000L,
				"<html>".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));

		assertThrows(IllegalArgumentException.class,
			() -> service.complete("usr_emma", pending.imageId()));
		verify(storage).delete(pending.objectKey());
	}

	@Test
	void createsAViewUrlOnlyForAnImageTheRepositoryMarksViewable() {
		ImageRecord ready = new ImageRecord(
			pending().imageId(),
			7L,
			pending().objectKey(),
			"image/jpeg",
			1_024L,
			1_000L,
			ImageStatus.READY,
			NOW,
			NOW);
		when(images.findViewable(ready.imageId(), "usr_emma"))
			.thenReturn(Optional.of(ready));
		when(storage.createViewUrl(ready.objectKey(), NOW))
			.thenReturn("https://signed.example/private-view");

		assertEquals(
			Optional.of("https://signed.example/private-view"),
			service.viewUrl(ready.imageId(), "usr_emma"));
		assertEquals(
			Optional.empty(),
			service.viewUrl("img_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null));
	}

	private static ImageRecord pending() {
		return new ImageRecord(
			"img_11111111222233334444555555555555",
			7L,
			"profile-images/usr_emma/11111111-2222-3333-4444-555555555555.jpg",
			"image/jpeg",
			1_024L,
			null,
			ImageStatus.PENDING,
			NOW,
			null);
	}
}
