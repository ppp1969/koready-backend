package koready_backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "koready.security.dev-principal.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NonLocalDevAuthenticationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theSameTokenIsNotAuthenticationOutsideTheLocalProfile() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/onboarding")
				.header(HttpHeaders.AUTHORIZATION, "Bearer local-user"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
