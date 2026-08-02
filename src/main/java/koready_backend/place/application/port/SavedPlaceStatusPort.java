package koready_backend.place.application.port;

import java.util.Collection;
import java.util.Set;

public interface SavedPlaceStatusPort {

	Set<Long> findSavedPlaceIds(String userPublicId, Collection<Long> placeIds);
}
