package koready_backend.terms.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.terms.application.TermsService;
import koready_backend.terms.application.TermsService.AgreementResult;
import koready_backend.terms.application.TermsService.RequiredTermsResult;
import koready_backend.terms.application.exception.InvalidTermAgreementException;
import koready_backend.terms.application.exception.RequiredTermsNotAgreedException;
import koready_backend.user.domain.NextStep;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TermsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TermsService service;

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/terms/required"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(put("/api/v1/users/me/term-agreements")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"agreements\":[]}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void returnsEmptyTermsWithoutBlockingSignup() throws Exception {
		when(service.getRequiredTerms("usr_terms"))
			.thenReturn(new RequiredTermsResult(List.of(), true));

		mockMvc.perform(get("/api/v1/terms/required")
				.with(user("usr_terms").roles("USER")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("REQUIRED_TERMS_RETRIEVED"))
			.andExpect(jsonPath("$.data.terms").isEmpty())
			.andExpect(jsonPath("$.data.allRequiredAgreed").value(true));
	}

	@Test
	void acceptsAnEmptyAgreementArrayWhenNoTermsArePublished() throws Exception {
		when(service.updateAgreements("usr_terms", List.of()))
			.thenReturn(new AgreementResult(
				List.of(), true, NextStep.LANGUAGE));

		mockMvc.perform(put("/api/v1/users/me/term-agreements")
				.with(user("usr_terms").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"agreements\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("TERM_AGREEMENTS_UPDATED"))
			.andExpect(jsonPath("$.data.agreements").isEmpty())
			.andExpect(jsonPath("$.data.nextStep").value("LANGUAGE"));
	}

	@Test
	void mapsMissingRequiredTermsToUnprocessableEntity() throws Exception {
		when(service.updateAgreements("usr_terms", List.of()))
			.thenThrow(new RequiredTermsNotAgreedException());

		mockMvc.perform(put("/api/v1/users/me/term-agreements")
				.with(user("usr_terms").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"agreements\":[]}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("REQUIRED_TERMS_NOT_AGREED"));
	}

	@Test
	void mapsUnknownOrDuplicateVersionsToBadRequest() throws Exception {
		when(service.updateAgreements(
			org.mockito.ArgumentMatchers.eq("usr_terms"),
			org.mockito.ArgumentMatchers.anyList()))
			.thenThrow(new InvalidTermAgreementException());

		mockMvc.perform(put("/api/v1/users/me/term-agreements")
				.with(user("usr_terms").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "agreements": [
					    {"termVersionId": 99, "agreed": true}
					  ]
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TERM_AGREEMENT"));
	}

	@Test
	void rejectsAMissingAgreementArray() throws Exception {
		mockMvc.perform(put("/api/v1/users/me/term-agreements")
				.with(user("usr_terms").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
