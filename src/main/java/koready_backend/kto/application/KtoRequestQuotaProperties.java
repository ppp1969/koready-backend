package koready_backend.kto.application;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "koready.kto.quota")
public record KtoRequestQuotaProperties(int defaultLimit, Map<String, Integer> limits) {
	public KtoRequestQuotaProperties {
		limits = limits == null ? Map.of() : Map.copyOf(limits);
		if (defaultLimit < 0 || limits.values().stream().anyMatch(value -> value < 0)
			|| limits.keySet().stream().anyMatch(key -> !key.matches("[a-z0-9-]{3,100}"))) {
			throw new IllegalArgumentException("KTO request quotas must be nonnegative");
		}
	}
	public int limit(String operation) {
		return limits.getOrDefault(operation, defaultLimit);
	}
}
