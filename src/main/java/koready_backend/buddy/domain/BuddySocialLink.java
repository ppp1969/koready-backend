package koready_backend.buddy.domain;

import java.util.Objects;

public record BuddySocialLink(
	SocialLinkType type,
	String value
) {

	public BuddySocialLink {
		type = Objects.requireNonNull(type, "Social link type is required");
		value = Objects.requireNonNull(value, "Social link value is required").trim();
		if (value.isEmpty() || value.length() > 200) {
			throw new IllegalArgumentException("Social link value must be 1 to 200 characters");
		}
	}
}
