package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoFetchedPhotoGalleryPage;
import koready_backend.kto.application.model.KtoPhotoGalleryImportRequest;
import koready_backend.kto.application.model.KtoPhotoGalleryImportResult;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageCommand;
import koready_backend.kto.application.model.KtoPhotoGalleryStorePageResult;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoPhotoGalleryPageClient;
import koready_backend.kto.application.port.KtoPhotoGalleryStore;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.domain.KtoPhotoGalleryPage;

@Service
public class KtoPhotoGalleryImportService {

	private static final String SNAPSHOT_SERVICE = "photo-gallery";
	private static final String OPERATION = "galleryList1";

	private final KtoPhotoGalleryPageClient client;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoPhotoGalleryStore store;
	private final Clock clock;

	@Autowired
	public KtoPhotoGalleryImportService(
		KtoPhotoGalleryPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoPhotoGalleryStore store
	) {
		this(client, snapshotStore, store, Clock.systemUTC());
	}

	KtoPhotoGalleryImportService(
		KtoPhotoGalleryPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoPhotoGalleryStore store,
		Clock clock
	) {
		this.client = client;
		this.snapshotStore = snapshotStore;
		this.store = store;
		this.clock = clock;
	}

	public KtoPhotoGalleryImportResult importGallery(
		KtoPhotoGalleryImportRequest request
	) {
		return importGallery(request, null);
	}

	public KtoPhotoGalleryImportResult importGallery(
		KtoPhotoGalleryImportRequest request,
		KtoBatchExecutionReference batchExecution
	) {
		int processedPages = 0;
		int processedItems = 0;
		int replayedPages = 0;
		int reportedTotalCount = 0;
		int lastProcessedPage = request.startPage();
		int reportedLastPage = Integer.MAX_VALUE;

		for (int pageNumber = request.startPage();
			processedPages < request.maxPages()
				&& pageNumber <= reportedLastPage;
			pageNumber++) {
			KtoFetchedPhotoGalleryPage fetched =
				client.fetchPage(pageNumber);
			KtoPhotoGalleryPage page = fetched.page();
			if (page.pageNumber() != pageNumber) {
				throw new KtoResponseParseException(
					"KTO photo gallery page number did not match the request");
			}
			reportedTotalCount = page.totalCount();
			reportedLastPage = page.totalCount() == 0
				? pageNumber
				: (page.totalCount() + page.pageSize() - 1)
					/ page.pageSize();
			Instant capturedAt = clock.instant();
			LocalDate snapshotDate = LocalDate.now(clock);
			KtoStoredSnapshotMetadata snapshot = snapshotStore.store(
				new KtoRawSnapshot(
					SNAPSHOT_SERVICE,
					OPERATION,
					snapshotDate,
					pageNumber,
					page.responseSha256(),
					fetched.rawPayload(),
					capturedAt));
			KtoPhotoGalleryStorePageResult stored = store.store(
				new KtoPhotoGalleryStorePageCommand(
					page,
					fetched.call(),
					snapshot,
					batchExecution));

			processedPages++;
			processedItems += stored.processedCount();
			replayedPages += stored.replayed() ? 1 : 0;
			lastProcessedPage = pageNumber;
		}

		return new KtoPhotoGalleryImportResult(
			request.startPage(),
			processedPages,
			processedItems,
			replayedPages,
			reportedTotalCount,
			lastProcessedPage,
			lastProcessedPage < reportedLastPage);
	}
}
