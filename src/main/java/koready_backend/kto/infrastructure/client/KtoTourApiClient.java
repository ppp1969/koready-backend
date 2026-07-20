package koready_backend.kto.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import koready_backend.kto.application.exception.KtoClientConfigurationException;
import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.application.port.KtoSyncPageClient;
import koready_backend.kto.application.port.KtoDailySyncPageClient;
import koready_backend.kto.application.model.KtoFetchedSyncPage;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.domain.KtoSyncPage;
import koready_backend.kto.infrastructure.config.KtoApiProperties;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;

@Component
public final class KtoTourApiClient implements KtoSyncPageClient, KtoDailySyncPageClient {

	private static final String OPERATION_PATH = "/areaBasedSyncList2";
	private static final int READ_BUFFER_BYTES = 8 * 1024;

	private final RestClient restClient;
	private final KtoApiProperties apiProperties;
	private final KtoBatchProperties batchProperties;
	private final KtoAreaBasedSyncResponseParser parser;
	private final Clock clock;

	@Autowired
	public KtoTourApiClient(
		@Qualifier("ktoRestClient") RestClient restClient,
		KtoApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoAreaBasedSyncResponseParser parser
	) {
		this(restClient, apiProperties, batchProperties, parser, Clock.systemUTC());
	}

	KtoTourApiClient(
		RestClient restClient,
		KtoApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoAreaBasedSyncResponseParser parser,
		Clock clock
	) {
		this.restClient = restClient;
		this.apiProperties = apiProperties;
		this.batchProperties = batchProperties;
		this.parser = parser;
		this.clock = clock;
	}

	@Override
	public KtoSyncPage fetchPage(int pageNumber) {
		return fetchFetchedPage(pageNumber).page();
	}

	@Override
	public KtoFetchedSyncPage fetchFetchedPage(int pageNumber) {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("KTO page number must be at least 1");
		}
		if (apiProperties.serviceKey() == null || apiProperties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException("KTO service key is not configured");
		}

		try {
			Instant requestedAt = Instant.now(clock);
			return restClient.get()
				.uri(uriBuilder -> uriBuilder
					.path(OPERATION_PATH)
					.queryParam("numOfRows", batchProperties.pageSize())
					.queryParam("pageNo", pageNumber)
					.queryParam("MobileOS", apiProperties.mobileOs())
					.queryParam("MobileApp", apiProperties.mobileApp())
					.queryParam("_type", "json")
					.queryParam("serviceKey", apiProperties.serviceKey())
					.build())
				.accept(MediaType.APPLICATION_JSON)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw KtoProviderException.forHttpStatus(response.getStatusCode().value());
					}
					long contentLength = response.getHeaders().getContentLength();
					if (contentLength > apiProperties.maxResponseBytes()) {
						throw new KtoResponseTooLargeException(apiProperties.maxResponseBytes());
					}
					byte[] payload = readBounded(response.getBody(), apiProperties.maxResponseBytes());
					KtoSyncPage page = parser.parse(payload);
					Instant receivedAt = Instant.now(clock);
					return new KtoFetchedSyncPage(page, new KtoSuccessfulCallMetadata(
						requestedAt, receivedAt, Duration.between(requestedAt, receivedAt).toMillis(),
						response.getStatusCode().value()), payload);
				});
		} catch (KtoProviderException | KtoResponseTooLargeException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new KtoTransportException();
		}
	}

	private byte[] readBounded(InputStream input, int maxResponseBytes) throws IOException {
		int initialCapacity = Math.min(64 * 1024, maxResponseBytes);
		var output = new ByteArrayOutputStream(initialCapacity);
		byte[] buffer = new byte[READ_BUFFER_BYTES];
		int totalBytes = 0;

		while (true) {
			int bytesToRead = Math.min(buffer.length, maxResponseBytes - totalBytes + 1);
			int bytesRead = input.read(buffer, 0, bytesToRead);
			if (bytesRead == -1) {
				return output.toByteArray();
			}
			if (bytesRead > maxResponseBytes - totalBytes) {
				throw new KtoResponseTooLargeException(maxResponseBytes);
			}
			output.write(buffer, 0, bytesRead);
			totalBytes += bytesRead;
		}
	}
}
