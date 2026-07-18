package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceItem;

public interface KtoCuratedPlaceClient {

	List<KtoPlaceItem> search(String keyword);

	KtoPlaceDetail fetchDetail(String contentId);
}
