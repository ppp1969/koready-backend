package koready_backend.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublishedOpenApiControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void publishesTheCanonicalContractWithJwtRolesAndActualResponses() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
				.value("bearer"))
			.andExpect(jsonPath("$.paths['/terms/required'].get.x-required-roles[0]")
				.value("USER"))
			.andExpect(jsonPath("$.paths['/admin/open-api/summary'].get.x-required-roles[0]")
				.value("ADMIN"))
			.andExpect(jsonPath("$.paths['/users/me/saved-places/{placeId}'].delete.responses['204']")
				.exists())
			.andExpect(jsonPath("$.paths['/profile-images/{imageId}'].get.responses['307']")
				.exists());
	}
}
