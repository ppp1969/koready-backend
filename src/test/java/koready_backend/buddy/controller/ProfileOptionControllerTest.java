package koready_backend.buddy.controller;

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
class ProfileOptionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsBackendOwnedProfileOptionsWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/profile-options"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("PROFILE_OPTIONS_OK"))
			.andExpect(jsonPath("$.data.languages[0].code").value("KO"))
			.andExpect(jsonPath("$.data.languages[0].labelKo").value("한국어"))
			.andExpect(jsonPath("$.data.languages[0].labelEn").value("Korean"))
			.andExpect(jsonPath("$.data.languages[4].code").value("VI"))
			.andExpect(jsonPath("$.data.koreanLevels[0].code").value("BEGINNER"))
			.andExpect(jsonPath("$.data.travelStyles[0].code").value("LOCAL_FOOD"))
			.andExpect(jsonPath("$.data.socialPlatforms[0].code").value("INSTAGRAM"))
			.andExpect(jsonPath("$.data.countries[?(@.code == 'KR')]").exists())
			.andExpect(jsonPath("$.data.countries[?(@.code == 'FR')]").exists());
	}
}
