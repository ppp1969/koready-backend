package koready_backend.evidence.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import koready_backend.evidence.application.EvidenceBundleService;
import koready_backend.externalapi.domain.ExternalApiProvider;

final class EvidenceBundleDtos {

	private EvidenceBundleDtos() {
	}

	record CreateRequest(
		@NotBlank @Size(max = 200) String name,
		@NotNull Instant from,
		@NotNull Instant to,
		@NotEmpty List<@NotNull ExternalApiProvider> providers,
		List<@NotBlank @Size(max = 100) String> operations,
		boolean includeRawSnapshots,
		@Min(0) @Max(100) int rawSampleLimitPerOperation
	) {
		EvidenceBundleService.CreateCommand toCommand() {
			return new EvidenceBundleService.CreateCommand(name, from, to, providers, operations,
				includeRawSnapshots, rawSampleLimitPerOperation);
		}
	}
}
