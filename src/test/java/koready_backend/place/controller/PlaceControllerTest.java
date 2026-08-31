package koready_backend.place.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.common.controller.TraceIdFilter;
import koready_backend.editorial.application.EditorialService;
import koready_backend.editorial.domain.EditorialJobStatus;
import koready_backend.editorial.domain.TourismPurposeTag;
import koready_backend.place.application.port.PlaceQueryRepository;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceDetailRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceRow;
import koready_backend.place.application.port.ResponseLanguageResolver;
import koready_backend.place.domain.PlaceLanguage;
import koready_backend.place.domain.ServiceRegionCode;
import koready_backend.place.domain.TravelStyle;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaceControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PlaceQueryRepository repository;

	@MockitoBean
	private ResponseLanguageResolver languageResolver;

	@MockitoBean
	private EditorialService editorialService;

	@BeforeEach
	void languageDefaults() {
		when(languageResolver.resolve(null, null)).thenReturn(PlaceLanguage.KO);
		when(languageResolver.resolve(null, "en-US")).thenReturn(PlaceLanguage.EN);
		when(editorialService.findOrEnqueue(any(Long.class), any(), any()))
			.thenReturn(new EditorialService.PublicEditorial(
				EditorialJobStatus.QUEUED, false, null));
		when(editorialService.findReadyCardContents(any(), any())).thenReturn(Map.of());
	}

	@Test
	void returnsPublicPlaceListWithTypedEnvelopeAndTraceId() throws Exception {
		when(repository.findByRegion(any())).thenReturn(List.of(new PlaceRow(
			1L,
			"Gyeongbokgung Palace",
			ServiceRegionCode.SEOUL,
			"Seoul",
			"Jongno-gu, Seoul",
			null,
			TravelStyle.CULTURE_EXPERIENCE,
			"A historic palace.",
			0,
			new BigDecimal("95.00"),
			null,
			null)));

		mockMvc.perform(get("/api/v1/places")
				.param("serviceRegionCode", "SEOUL")
				.param("travelStyles", "CULTURE_EXPERIENCE")
				.header("Accept-Language", "en-US"))
			.andExpect(status().isOk())
			.andExpect(header().exists(TraceIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.code").value("PLACE_LIST_OK"))
			.andExpect(jsonPath("$.traceId").isNotEmpty())
			.andExpect(jsonPath("$.data.items", hasSize(1)))
			.andExpect(jsonPath("$.data.items[0].title").value("Gyeongbokgung Palace"))
			.andExpect(jsonPath("$.data.items[0].travelStyle")
				.value("CULTURE_EXPERIENCE"))
			.andExpect(jsonPath("$.data.items[0].saved").value(false));
	}

	@Test
	void returnsNullableDetailFieldsWithoutInventingData() throws Exception {
		when(repository.findDetail(any(Long.class), any())).thenReturn(Optional.of(
			new PlaceDetailRow(
				3L,
				"Place",
				ServiceRegionCode.JEJU,
				"제주",
				null,
				null,
				null,
				null,
				null,
				"KTO_KO")));
		when(repository.findPrimaryTravelStyle(3L))
			.thenReturn(Optional.of(TravelStyle.NATURE));

		mockMvc.perform(get("/api/v1/places/3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.placeId").value(3))
			.andExpect(jsonPath("$.data.address").value((Object) null))
			.andExpect(jsonPath("$.data.operatingHours").value((Object) null))
			.andExpect(jsonPath("$.data.images", hasSize(0)))
			.andExpect(jsonPath("$.data.travelStyle").value("NATURE"))
			.andExpect(jsonPath("$.data.availableTabs", hasSize(1)))
			.andExpect(jsonPath("$.data.availableTabs[0]").value("MATES"))
			.andExpect(jsonPath("$.data.editorialStatus").value("QUEUED"))
			.andExpect(jsonPath("$.data.description").value((Object) null))
			.andExpect(jsonPath("$.data.isSaved").value(false));
	}

	@Test
	void returnsEditorialMetadataForRelatedPlaces() throws Exception {
		when(repository.findDetail(10L, PlaceLanguage.KO)).thenReturn(Optional.of(
			new PlaceDetailRow(
				10L, "기준 장소", ServiceRegionCode.SEOUL, "서울", "주소",
				null, null, null, "긴 KTO 원문", "KTO_KO")));
		when(repository.findRelatedPlacesWithSameStyle(
			10L, PlaceLanguage.KO, List.of(10L), 3))
			.thenReturn(List.of(new PlaceQueryRepository.RelatedPlaceRow(
				20L, "연관 장소", "https://example.invalid/related.jpg", "긴 원문")));
		when(repository.findPrimaryTravelStyles(List.of(20L)))
			.thenReturn(Map.of(20L, TravelStyle.TRADITIONAL_MARKET));
		when(editorialService.findReadyCardContents(eq(List.of(20L)), any()))
			.thenReturn(Map.of(20L, new EditorialService.CardEditorialContent(
				"시장 문화를 가까이에서 경험할 수 있어요.",
				List.of(TourismPurposeTag.LOCAL, TourismPurposeTag.EXPLORATION))));

		mockMvc.perform(get("/api/v1/places/10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.relatedPlaces[0].travelStyle")
				.value("TRADITIONAL_MARKET"))
			.andExpect(jsonPath("$.data.relatedPlaces[0].tags[0]").value("#로컬"))
			.andExpect(jsonPath("$.data.relatedPlaces[0].shortDescription")
				.value("시장 문화를 가까이에서 경험할 수 있어요."));
	}

	@Test
	void returnsDocumentedErrorsForMissingPlaceAndInvalidCursor() throws Exception {
		when(repository.findDetail(any(Long.class), any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/v1/places/999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"))
			.andExpect(jsonPath("$.traceId").isNotEmpty());

		mockMvc.perform(get("/api/v1/places")
				.param("serviceRegionCode", "SEOUL")
				.param("cursor", "not-a-cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
	}

	@Test
	void rejectsInvalidEnumsAndPageSizesBeforeQueryingDatabase() throws Exception {
		mockMvc.perform(get("/api/v1/places")
				.param("serviceRegionCode", "UNKNOWN"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/api/v1/places/search")
				.param("query", " ")
				.param("size", "51"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
