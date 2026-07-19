package koready_backend.place.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.common.controller.TraceIdFilter;
import koready_backend.place.application.port.PlaceQueryRepository;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceDetailRow;
import koready_backend.place.application.port.PlaceQueryRepository.PlaceRow;
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

		mockMvc.perform(get("/api/v1/places/3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.placeId").value(3))
			.andExpect(jsonPath("$.data.address").value((Object) null))
			.andExpect(jsonPath("$.data.operatingHours").value((Object) null))
			.andExpect(jsonPath("$.data.images", hasSize(0)))
			.andExpect(jsonPath("$.data.availableTabs", hasSize(1)))
			.andExpect(jsonPath("$.data.availableTabs[0]").value("MATES"))
			.andExpect(jsonPath("$.data.isSaved").value(false));
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
