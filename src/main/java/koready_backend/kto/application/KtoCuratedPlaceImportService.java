package koready_backend.kto.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import koready_backend.kto.application.port.KtoCuratedPlaceClient;
import koready_backend.kto.application.port.KtoCuratedPlaceStore;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.onboarding.domain.InitialCandidatePlace;
import koready_backend.onboarding.domain.InitialCandidatePlaceCatalog;

@Service
public class KtoCuratedPlaceImportService {

	private final KtoCuratedPlaceClient client;
	private final KtoCuratedPlaceStore store;

	public KtoCuratedPlaceImportService(
		KtoCuratedPlaceClient client,
		KtoCuratedPlaceStore store
	) {
		this.client = client;
		this.store = store;
	}

	public Map<String, Long> importApprovedCatalog() {
		Map<String, Long> placeIds = new LinkedHashMap<>();
		for (InitialCandidatePlace specification : InitialCandidatePlaceCatalog.approved()) {
			KtoPlaceItem searchItem = requirePinnedSearchItem(
				specification,
				client.search(specification.searchKeyword()));
			validateMetadata(specification, searchItem, "search");

			KtoPlaceDetail detail = client.fetchDetail(specification.ktoContentId());
			validateMetadata(specification, detail.place(), "detail");
			validateRequiredFields(specification, detail.place());
			List<KtoPlaceImage> images = client.fetchImages(specification.ktoContentId());

			long placeId = store.upsert(specification, detail, images);
			if (placeId <= 0) {
				throw new IllegalStateException("Curated KTO place store returned an invalid place ID");
			}
			placeIds.put(specification.ktoContentId(), placeId);
		}
		if (placeIds.size() != InitialCandidatePlaceCatalog.approved().size()) {
			throw new IllegalStateException("Not all approved KTO places were imported");
		}
		return Map.copyOf(placeIds);
	}

	private KtoPlaceItem requirePinnedSearchItem(
		InitialCandidatePlace specification,
		List<KtoPlaceItem> searchItems
	) {
		List<KtoPlaceItem> matches = searchItems.stream()
			.filter(item -> specification.ktoContentId().equals(item.contentId()))
			.toList();
		if (matches.size() != 1) {
			throw new IllegalStateException(
				"KTO keyword result did not contain exactly one pinned content ID: "
					+ specification.ktoContentId());
		}
		return matches.getFirst();
	}

	private void validateMetadata(
		InitialCandidatePlace specification,
		KtoPlaceItem item,
		String operation
	) {
		if (!specification.ktoContentId().equals(item.contentId())
			|| !specification.ktoContentTypeId().equals(item.contentTypeId())
			|| !specification.expectedKtoTitleKo().equals(item.title())) {
			throw new IllegalStateException(
				"Pinned KTO metadata changed during " + operation + ": "
					+ specification.ktoContentId());
		}
	}

	private void validateRequiredFields(
		InitialCandidatePlace specification,
		KtoPlaceItem item
	) {
		if (blank(item.address1()) && blank(item.address2())) {
			throw missing(specification, "address");
		}
		if (blank(item.latitude()) || blank(item.longitude())) {
			throw missing(specification, "coordinates");
		}
		if (blank(item.primaryImageUrl())) {
			throw missing(specification, "primary image");
		}
		if (blank(item.areaCode()) && blank(item.legalDongRegionCode())) {
			throw missing(specification, "region code");
		}
	}

	private IllegalStateException missing(
		InitialCandidatePlace specification,
		String field
	) {
		return new IllegalStateException(
			"Approved KTO place is missing " + field + ": " + specification.ktoContentId());
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
