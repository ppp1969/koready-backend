package koready_backend.editorial.application.port;

import koready_backend.editorial.domain.EditorialGeneration;

public interface EditorialGenerator {

	EditorialGeneration generate(EditorialWorkerRepository.GenerationSource source);
}
