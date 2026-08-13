package koready_backend.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void nonHealthEndpointIsDeniedUntilAuthenticationIsImplemented() throws Exception {
		mockMvc.perform(get("/api/v1"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void placeReadsArePublicButPlaceWritesRemainProtected() throws Exception {
		mockMvc.perform(get("/api/v1/places"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/places/search"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/places/not-a-number"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/places"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void monthlyRecommendationReadIsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/monthly-recommendations"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void allowsPublicReadsFromAnyDevelopmentOrigin() throws Exception {
		mockMvc.perform(get("/actuator/health")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173"))
			.andExpect(status().isOk())
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
	}

	@Test
	void allowsAuthenticatedApiPreflightFromAnyDevelopmentOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/users/me")
				.header(HttpHeaders.ORIGIN, "https://frontend-preview.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
				.header(
					HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
					"authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
				containsString("PATCH")))
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
				containsString("authorization")));
	}

	@Test
	void swaggerUiAndTypedContractArePublic() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/swagger-ui/index.html"));

		mockMvc.perform(get("/swagger-ui/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/v3/api-docs")));

		mockMvc.perform(get("/webjars/swagger-ui/swagger-ui.css"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/openapi/koready.yaml"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("TokenEnvelope:")))
			.andExpect(content().string(containsString("HomeEnvelope:")))
			.andExpect(content().string(containsString("MessageThreadEnvelope:")))
			.andExpect(content().string(containsString("RouteFare:")))
			.andExpect(content().string(containsString("RouteWarning:")))
			.andExpect(content().string(containsString("/admin/hori-tips:")))
			.andExpect(content().string(containsString("AdminHoriTipEnvelope:")))
			.andExpect(content().string(containsString("OPERATOR_CURATED")))
			.andExpect(content().string(not(containsString("#/components/responses/Success"))));
	}
}
