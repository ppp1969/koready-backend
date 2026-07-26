package koready_backend.kto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class KtoEnglishPlaceMatcherTest {

	@Test
	void confirmsOneNormalizedImagePathBeforeCoordinateEvidence() {
		String imageHash = KtoEnglishMatchKeyFactory.imagePathHash(
			"https://images.example.invalid/shared/place.jpg?width=800");
		String coordinateKey = KtoEnglishMatchKeyFactory.koreanCoordinateContentTypeKey(
			"126.978", "37.5665", "12");
		var matcher = new KtoEnglishPlaceMatcher(List.of(
			new KtoEnglishPlaceCandidate(11L, imageHash, coordinateKey)));

		KtoEnglishMatchDecision decision = matcher.match(item(
			"https://cdn.example.invalid/shared/place.jpg",
			"126.9780004",
			"37.5665004",
			"76"));

		assertEquals(KtoEnglishMatchStatus.AUTO_CONFIRMED, decision.status());
		assertEquals(KtoEnglishMatchMethod.IMAGE_PATH, decision.method());
		assertEquals(11L, decision.confirmedPlaceId());
	}

	@Test
	void confirmsOneRoundedCoordinateAndMappedContentTypeWithoutAnImage() {
		String coordinateKey = KtoEnglishMatchKeyFactory.koreanCoordinateContentTypeKey(
			"127.123456", "36.654321", "14");
		var matcher = new KtoEnglishPlaceMatcher(List.of(
			new KtoEnglishPlaceCandidate(21L, null, coordinateKey)));

		KtoEnglishMatchDecision decision = matcher.match(item(
			null,
			"127.1234564",
			"36.6543214",
			"78"));

		assertEquals(KtoEnglishMatchStatus.AUTO_CONFIRMED, decision.status());
		assertEquals(KtoEnglishMatchMethod.COORDINATE_CONTENT_TYPE, decision.method());
		assertEquals(21L, decision.confirmedPlaceId());
	}

	@Test
	void requiresReviewWhenImageAndCoordinateEvidenceConflict() {
		String imageHash = KtoEnglishMatchKeyFactory.imagePathHash(
			"https://images.example.invalid/conflict.jpg");
		String coordinateKey = KtoEnglishMatchKeyFactory.koreanCoordinateContentTypeKey(
			"128.1", "35.1", "12");
		var matcher = new KtoEnglishPlaceMatcher(List.of(
			new KtoEnglishPlaceCandidate(31L, imageHash, null),
			new KtoEnglishPlaceCandidate(32L, null, coordinateKey)));

		KtoEnglishMatchDecision decision = matcher.match(item(
			"https://other.example.invalid/conflict.jpg",
			"128.1",
			"35.1",
			"76"));

		assertEquals(KtoEnglishMatchStatus.REVIEW_REQUIRED, decision.status());
		assertEquals(KtoEnglishMatchMethod.EVIDENCE_CONFLICT, decision.method());
		assertEquals(List.of(31L, 32L), decision.candidates().stream()
			.map(KtoEnglishPlaceCandidate::placeId)
			.toList());
		assertNull(decision.confirmedPlaceId());
	}

	@Test
	void leavesAnItemUnmatchedWhenNoSafeEvidenceExists() {
		var matcher = new KtoEnglishPlaceMatcher(List.of());

		KtoEnglishMatchDecision decision = matcher.match(item(null, null, null, "76"));

		assertEquals(KtoEnglishMatchStatus.UNMATCHED, decision.status());
		assertEquals(KtoEnglishMatchMethod.NONE, decision.method());
		assertNull(decision.confirmedPlaceId());
	}

	private KtoEnglishPlaceItem item(
		String image,
		String longitude,
		String latitude,
		String contentTypeId
	) {
		return new KtoEnglishPlaceItem(
			"eng-1",
			"old-eng-1",
			contentTypeId,
			"English place",
			"Seoul",
			null,
			image,
			null,
			longitude,
			latitude,
			"20260701090000",
			"1",
			"a".repeat(64));
	}
}
