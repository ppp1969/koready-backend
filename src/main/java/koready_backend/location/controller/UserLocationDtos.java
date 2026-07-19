package koready_backend.location.controller;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import koready_backend.location.application.UserLocationService;
import koready_backend.place.domain.ServiceRegionCode;

final class UserLocationDtos {

	private UserLocationDtos() {
	}

	static UserLocationListResponse from(UserLocationService.LocationList result) {
		return new UserLocationListResponse(result.items().stream()
			.map(UserLocationDtos::from)
			.toList());
	}

	static UserLocationResponse from(UserLocationService.Location location) {
		return new UserLocationResponse(
			location.locationId(),
			location.displayName(),
			location.customLabel(),
			location.roadAddress(),
			location.address(),
			location.latitude(),
			location.longitude(),
			location.serviceRegionCode(),
			location.isDefault(),
			location.createdAt());
	}

	record CreateLocationRequest(
		@NotBlank @Size(max = 8192) String searchResultToken,
		@Size(max = 30) String customLabel,
		@NotNull Boolean setDefault
	) {
		UserLocationService.CreateCommand toCommand() {
			return new UserLocationService.CreateCommand(
				searchResultToken, customLabel, setDefault.booleanValue());
		}
	}

	record UserLocationListResponse(List<UserLocationResponse> items) {
		UserLocationListResponse {
			items = List.copyOf(items);
		}
	}

	record UserLocationResponse(
		long locationId,
		String displayName,
		String customLabel,
		String roadAddress,
		String address,
		double latitude,
		double longitude,
		ServiceRegionCode serviceRegionCode,
		@JsonProperty("default") boolean isDefault,
		Instant createdAt
	) {
	}
}
