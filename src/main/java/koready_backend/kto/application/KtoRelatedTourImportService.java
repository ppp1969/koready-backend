package koready_backend.kto.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import koready_backend.kto.application.exception.KtoResponseParseException;
import koready_backend.kto.application.model.KtoBatchExecutionReference;
import koready_backend.kto.application.model.KtoFetchedRelatedTourPage;
import koready_backend.kto.application.model.KtoRawSnapshot;
import koready_backend.kto.application.model.KtoRelatedTourImportRequest;
import koready_backend.kto.application.model.KtoRelatedTourImportResult;
import koready_backend.kto.application.model.KtoRelatedTourRegion;
import koready_backend.kto.application.model.KtoRelatedTourStorePageCommand;
import koready_backend.kto.application.model.KtoRelatedTourStorePageResult;
import koready_backend.kto.application.model.KtoStoredSnapshotMetadata;
import koready_backend.kto.application.port.KtoRawSnapshotStore;
import koready_backend.kto.application.port.KtoRelatedTourPageClient;
import koready_backend.kto.application.port.KtoRelatedTourRegionSource;
import koready_backend.kto.application.port.KtoRelatedTourStore;
import koready_backend.kto.domain.KtoRelatedTourPage;

@Service
public class KtoRelatedTourImportService {

	private static final String SNAPSHOT_SERVICE = "related-tour";
	private static final String OPERATION = "areaBasedList1";

	private final KtoRelatedTourRegionSource regionSource;
	private final KtoRelatedTourPageClient client;
	private final KtoRawSnapshotStore snapshotStore;
	private final KtoRelatedTourStore store;
	private final Clock clock;

	@Autowired
	public KtoRelatedTourImportService(
		KtoRelatedTourRegionSource regionSource,
		KtoRelatedTourPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoRelatedTourStore store
	) {
		this(
			regionSource,
			client,
			snapshotStore,
			store,
			Clock.systemUTC());
	}

	KtoRelatedTourImportService(
		KtoRelatedTourRegionSource regionSource,
		KtoRelatedTourPageClient client,
		KtoRawSnapshotStore snapshotStore,
		KtoRelatedTourStore store,
		Clock clock
	) {
		this.regionSource = regionSource;
		this.client = client;
		this.snapshotStore = snapshotStore;
		this.store = store;
		this.clock = clock;
	}

	public KtoRelatedTourImportResult importRelatedTours(
		KtoRelatedTourImportRequest request
	) {
		return importRelatedTours(request, null);
	}

	public KtoRelatedTourImportResult importRelatedTours(
		KtoRelatedTourImportRequest request,
		KtoBatchExecutionReference batchExecution
	) {
		List<KtoRelatedTourRegion> candidates = regionSource.findAfter(
			request.baseYearMonth(),
			request.startAfterRegionKey(),
			request.maxRegions() + 1);
		boolean hasMore = candidates.size() > request.maxRegions();
		List<KtoRelatedTourRegion> regions = candidates.subList(
			0, Math.min(request.maxRegions(), candidates.size()));
		int processedPages = 0;
		int processedItems = 0;
		int replayedPages = 0;
		String lastRegionKey = request.startAfterRegionKey();

		for (KtoRelatedTourRegion region : regions) {
			int pageNumber = 1;
			int reportedLastPage = 1;
			do {
				if (pageNumber > request.maxPagesPerRegion()) {
					throw new KtoResponseParseException(
						"KTO related tour region exceeded its page limit");
				}
				KtoFetchedRelatedTourPage fetched = client.fetchPage(
					request.baseYearMonth(),
					region.providerAreaCode(),
					region.providerSignguCode(),
					pageNumber);
				KtoRelatedTourPage page = fetched.page();
				if (page.pageNumber() != pageNumber) {
					throw new KtoResponseParseException(
						"KTO related tour page number did not match the request");
				}
				validateScope(
					request.baseYearMonth(), region, page);
				reportedLastPage = page.totalCount() == 0
					? 1
					: (page.totalCount() + page.pageSize() - 1)
						/ page.pageSize();
				Instant capturedAt = clock.instant();
				KtoStoredSnapshotMetadata snapshot = snapshotStore.store(
					new KtoRawSnapshot(
						SNAPSHOT_SERVICE,
						snapshotOperation(
							request.baseYearMonth(), region),
						LocalDate.now(clock),
						pageNumber,
						page.responseSha256(),
						fetched.rawPayload(),
						capturedAt));
				KtoRelatedTourStorePageResult stored = store.store(
					new KtoRelatedTourStorePageCommand(
						request.baseYearMonth(),
						region,
						page,
						fetched.call(),
						snapshot,
						batchExecution));
				processedPages++;
				processedItems += stored.processedCount();
				replayedPages += stored.replayed() ? 1 : 0;
				pageNumber++;
			} while (pageNumber <= reportedLastPage);
			lastRegionKey = region.key();
		}

		return new KtoRelatedTourImportResult(
			regions.size(),
			processedPages,
			processedItems,
			replayedPages,
			lastRegionKey,
			hasMore,
			request.autoContinue());
	}

	private static void validateScope(
		String baseYearMonth,
		KtoRelatedTourRegion region,
		KtoRelatedTourPage page
	) {
		boolean outsideRequest = page.items().stream().anyMatch(item ->
			!baseYearMonth.equals(item.baseYearMonth())
				|| !region.providerAreaCode().equals(item.areaCode())
				|| !region.providerSignguCode()
					.equals(item.signguCode()));
		if (outsideRequest) {
			throw new KtoResponseParseException(
				"KTO related tour item did not match the requested scope");
		}
	}

	private static String snapshotOperation(
		String baseYearMonth,
		KtoRelatedTourRegion region
	) {
		return OPERATION
			+ baseYearMonth
			+ region.providerAreaCode()
			+ region.providerSignguCode();
	}
}
