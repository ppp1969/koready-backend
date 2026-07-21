package koready_backend.kto.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import koready_backend.kto.application.port.KtoCuratedPlaceClient;
import koready_backend.kto.application.port.KtoCuratedPlaceStore;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.onboarding.domain.InitialCandidatePlace;
import koready_backend.onboarding.domain.InitialCandidatePlaceCatalog;

class KtoCuratedPlaceImportServiceTest {

	@Test
	void importsOnlyTheApprovedCatalogAndReturnsStablePlaceIds() {
		List<String> searches = new ArrayList<>();
		List<String> stored = new ArrayList<>();
		Map<String, KtoPlaceItem> items = approvedItems();
		KtoCuratedPlaceClient client = new KtoCuratedPlaceClient() {
			@Override
			public List<KtoPlaceItem> search(String keyword) {
				searches.add(keyword);
				return items.values().stream()
					.filter(item -> catalog(item.contentId()).searchKeyword().equals(keyword))
					.toList();
			}

			@Override
			public KtoPlaceDetail fetchDetail(String contentId) {
				return new KtoPlaceDetail(items.get(contentId), "Overview", "https://example.invalid");
			}

			@Override
			public List<KtoPlaceImage> fetchImages(String contentId) {
				return detailImages();
			}
		};
		KtoCuratedPlaceStore store = (specification, detail) -> {
			stored.add(specification.ktoContentId());
			return 1_000L + specification.displayOrder();
		};

		Map<String, Long> result = new KtoCuratedPlaceImportService(client, store)
			.importApprovedCatalog();

		assertEquals(
			InitialCandidatePlaceCatalog.approved().stream()
				.map(InitialCandidatePlace::searchKeyword)
				.toList(),
			searches);
		assertEquals(
			InitialCandidatePlaceCatalog.approved().stream()
				.map(InitialCandidatePlace::ktoContentId)
				.toList(),
			stored);
		assertEquals(10, result.size());
		assertEquals(1_001L, result.get("126508"));
	}

	@Test
	void rejectsAKeywordSearchThatDoesNotContainThePinnedContentId() {
		KtoCuratedPlaceClient client = clientReturning(List.of());
		KtoCuratedPlaceStore store = (specification, detail) -> 1L;

		assertThrows(
			IllegalStateException.class,
			() -> new KtoCuratedPlaceImportService(client, store).importApprovedCatalog());
	}

	@Test
	void rejectsAmbiguousDuplicateMatchesForThePinnedContentId() {
		InitialCandidatePlace first = InitialCandidatePlaceCatalog.approved().getFirst();
		KtoPlaceItem item = item(first);
		KtoCuratedPlaceClient client = clientReturning(List.of(item, item));
		KtoCuratedPlaceStore store = (specification, detail) -> 1L;

		assertThrows(
			IllegalStateException.class,
			() -> new KtoCuratedPlaceImportService(client, store).importApprovedCatalog());
	}

	@Test
	void rejectsChangedKtoMetadataBeforeWriting() {
		InitialCandidatePlace first = InitialCandidatePlaceCatalog.approved().getFirst();
		KtoPlaceItem changed = withTitle(item(first), "Changed title");
		KtoCuratedPlaceClient client = clientReturning(List.of(changed));
		KtoCuratedPlaceStore store = (specification, detail) -> 1L;

		assertThrows(
			IllegalStateException.class,
			() -> new KtoCuratedPlaceImportService(client, store).importApprovedCatalog());
	}

	private static KtoCuratedPlaceClient clientReturning(List<KtoPlaceItem> searchResult) {
		return new KtoCuratedPlaceClient() {
			@Override
			public List<KtoPlaceItem> search(String keyword) {
				return searchResult;
			}

			@Override
			public KtoPlaceDetail fetchDetail(String contentId) {
				return new KtoPlaceDetail(searchResult.getFirst(), "Overview", null);
			}

			@Override
			public List<KtoPlaceImage> fetchImages(String contentId) {
				return detailImages();
			}
		};
	}

	@Test
	void rejectsAnApprovedPlaceWithFewerThanFourDistinctImages() {
		Map<String, KtoPlaceItem> items = approvedItems();
		KtoCuratedPlaceClient client = new KtoCuratedPlaceClient() {
			@Override
			public List<KtoPlaceItem> search(String keyword) {
				return items.values().stream()
					.filter(item -> catalog(item.contentId()).searchKeyword().equals(keyword)).toList();
			}

			@Override
			public KtoPlaceDetail fetchDetail(String contentId) {
				return new KtoPlaceDetail(items.get(contentId), "Overview", null);
			}

			@Override
			public List<KtoPlaceImage> fetchImages(String contentId) {
				return List.of(new KtoPlaceImage(
					"https://example.invalid/only-detail.jpg", null, null, "Type1", 1));
			}
		};

		assertThrows(IllegalStateException.class,
			() -> new KtoCuratedPlaceImportService(client, (specification, detail) -> 1L)
				.importApprovedCatalog());
	}

	private static List<KtoPlaceImage> detailImages() {
		return List.of(
			new KtoPlaceImage("https://example.invalid/detail-1.jpg", null, null, "Type1", 1),
			new KtoPlaceImage("https://example.invalid/detail-2.jpg", null, null, "Type1", 2),
			new KtoPlaceImage("https://example.invalid/detail-3.jpg", null, null, "Type1", 3));
	}

	private static Map<String, KtoPlaceItem> approvedItems() {
		Map<String, KtoPlaceItem> items = new HashMap<>();
		InitialCandidatePlaceCatalog.approved()
			.forEach(place -> items.put(place.ktoContentId(), item(place)));
		return items;
	}

	private static InitialCandidatePlace catalog(String contentId) {
		return InitialCandidatePlaceCatalog.approved().stream()
			.filter(place -> place.ktoContentId().equals(contentId))
			.findFirst()
			.orElseThrow();
	}

	private static KtoPlaceItem item(InitialCandidatePlace place) {
		return new KtoPlaceItem(
			place.ktoContentId(), place.ktoContentTypeId(), place.expectedKtoTitleKo(),
			"서울특별시 종로구", null, "1", "1", null, null, null, null,
			"20260101000000", "https://example.invalid/image.jpg", null,
			"126.978", "37.5665", "6", "20260718000000", null, "03045", null,
			"11", "110", "VE", "VE01", "VE010100", "a".repeat(64));
	}

	private static KtoPlaceItem withTitle(KtoPlaceItem item, String title) {
		return new KtoPlaceItem(
			item.contentId(), item.contentTypeId(), title, item.address1(), item.address2(),
			item.areaCode(), item.districtCode(), item.categoryCode1(), item.categoryCode2(),
			item.categoryCode3(), item.copyrightType(), item.createdTime(), item.primaryImageUrl(),
			item.thumbnailImageUrl(), item.longitude(), item.latitude(), item.mapLevel(),
			item.modifiedTime(), item.phoneNumber(), item.postalCode(), item.showFlag(),
			item.legalDongRegionCode(), item.legalDongDistrictCode(), item.classificationCode1(),
			item.classificationCode2(), item.classificationCode3(), item.sourceHash());
	}
}
