package koready_backend.terms.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import koready_backend.terms.application.AdminTermsService;
import koready_backend.terms.application.port.AdminTermsRepository.TermVersion;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTermsControllerTest {
	@Autowired
	MockMvc mockMvc;
	@MockitoBean
	AdminTermsService service;

	@Test
	void createsAnInlineMarkdownDraft() throws Exception {
		when(service.createVersion(eq(1L), any())).thenAnswer(invocation -> {
			AdminTermsService.VersionCommand command = invocation.getArgument(1);
			return new TermVersion(2, 1, command.version(), command.title(), command.contentUrl(),
				command.content(), command.contentFormat(), command.required(), command.effectiveAt(), null, null);
		});

		mockMvc.perform(post("/api/v1/admin/terms/1/versions")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"version":"1.0","title":"서비스 이용약관","content":"# 약관\\n본문",
					 "contentFormat":"MARKDOWN","required":true,"effectiveAt":"2026-09-10T00:00:00Z"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sourceType").value("INLINE"))
			.andExpect(jsonPath("$.data.content").value("# 약관\n본문"))
			.andExpect(jsonPath("$.data.contentFormat").value("MARKDOWN"))
			.andExpect(jsonPath("$.data.contentUrl").doesNotExist());
	}

	@Test
	void keepsTheExistingExternalUrlRequestCompatible() throws Exception {
		when(service.createVersion(eq(1L), any())).thenAnswer(invocation -> {
			AdminTermsService.VersionCommand command = invocation.getArgument(1);
			return new TermVersion(2, 1, command.version(), command.title(), command.contentUrl(),
				command.content(), command.contentFormat(), command.required(), command.effectiveAt(), null, null);
		});

		mockMvc.perform(post("/api/v1/admin/terms/1/versions")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"version":"1.0","title":"개인정보처리방침",
					 "contentUrl":"https://www.notion.so/example","required":true,
					 "effectiveAt":"2026-09-10T00:00:00Z"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sourceType").value("EXTERNAL_URL"))
			.andExpect(jsonPath("$.data.contentUrl").value("https://www.notion.so/example"))
			.andExpect(jsonPath("$.data.content").doesNotExist());
	}

	@Test
	void createsKoreanAndEnglishContentUnderOneVersion() throws Exception {
		when(service.createVersion(eq(1L), any())).thenAnswer(invocation -> {
			AdminTermsService.VersionCommand command = invocation.getArgument(1);
			return new TermVersion(2, 1, command.version(), "서비스 이용약관", null,
				"한국어 본문", koready_backend.terms.domain.TermContentFormat.MARKDOWN,
				command.required(), command.effectiveAt(), null, null, command.translations());
		});

		mockMvc.perform(post("/api/v1/admin/terms/1/versions")
				.with(user("admin").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"version":"1.0","required":true,"effectiveAt":"2026-09-10T00:00:00Z",
					 "translations":[
					  {"language":"KO","title":"서비스 이용약관","content":"한국어 본문","contentFormat":"MARKDOWN"},
					  {"language":"EN","title":"Terms of Service","content":"English content","contentFormat":"MARKDOWN"}
					 ]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(2))
			.andExpect(jsonPath("$.data.translations.length()").value(2))
			.andExpect(jsonPath("$.data.translations[1].language").value("EN"));
	}
}
