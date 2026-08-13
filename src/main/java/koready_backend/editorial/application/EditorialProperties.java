package koready_backend.editorial.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("koready.editorial")
public record EditorialProperties(
	String promptVersion,
	boolean publicationFilterEnabled
) {
	public EditorialProperties {
		promptVersion = promptVersion == null || promptVersion.isBlank()
			? "koready-place-editorial-v1"
			: promptVersion.strip();
	}
}
