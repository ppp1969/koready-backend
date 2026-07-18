package koready_backend.horitip.domain;

import java.util.HashSet;
import java.util.List;

public record HoriTipScope(
	HoriTipScopeType scopeType,
	List<Long> destinationPlaceIds
) {

	private static final int MAX_DESTINATIONS = 100;

	public HoriTipScope {
		if (scopeType == null || destinationPlaceIds == null) {
			throw invalid("A Hori Tip scope type and destination list are required");
		}
		destinationPlaceIds = List.copyOf(destinationPlaceIds);
		if (destinationPlaceIds.size() > MAX_DESTINATIONS
			|| destinationPlaceIds.stream().anyMatch(id -> id == null || id <= 0)
			|| new HashSet<>(destinationPlaceIds).size() != destinationPlaceIds.size()) {
			throw invalid("Destination place IDs must be positive, unique, and limited to 100");
		}
		if (scopeType == HoriTipScopeType.ALL_ROUTES && !destinationPlaceIds.isEmpty()) {
			throw invalid("ALL_ROUTES cannot contain destination place IDs");
		}
		if (scopeType == HoriTipScopeType.DESTINATION_PLACES
			&& destinationPlaceIds.isEmpty()) {
			throw invalid("DESTINATION_PLACES requires at least one destination place ID");
		}
	}

	private static HoriTipPolicyException invalid(String message) {
		return new HoriTipPolicyException(HoriTipPolicyException.Reason.RULE_INVALID, message);
	}
}
