package koready_backend.kto.application.port;

import java.util.List;

import koready_backend.kto.domain.KtoPhotoAwardImage;

public interface KtoPhotoAwardClient {

	List<KtoPhotoAwardImage> fetchAll();
}
