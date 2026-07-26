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
import koready_backend.kto.application.model.KtoFetchedEnglishSyncPage;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoEnglishSyncPageClient;
import koready_backend.kto.infrastructure.config.KtoBatchProperties;
import koready_backend.kto.infrastructure.config.KtoEnglishApiProperties;

@Component
public final class KtoEnglishTourApiClient implements KtoEnglishSyncPageClient {

	private static final String OPERATION_PATH = "/areaBasedSyncList2";
	private static final int READ_BUFFER_BYTES = 8 * 1024;
	private static final int MAX_TRANSIENT_ATTEMPTS = 4;
	private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000;

	private final RestClient restClient;
	private final KtoEnglishApiProperties apiProperties;
	private final KtoBatchProperties batchProperties;
	private final KtoEnglishAreaBasedSyncResponseParser parser;
	private final Clock clock;
	private final RetrySleeper retrySleeper;

	@Autowired
	public KtoEnglishTourApiClient(
		@Qualifier("ktoEnglishRestClient") RestClient restClient,
		KtoEnglishApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoEnglishAreaBasedSyncResponseParser parser
	) {
		this(restClient, apiProperties, batchProperties, parser, Clock.systemUTC(), Thread::sleep);
	}

	KtoEnglishTourApiClient(
		RestClient restClient,
		KtoEnglishApiProperties apiProperties,
		KtoBatchProperties batchProperties,
		KtoEnglishAreaBasedSyncResponseParser parser,
		Clock clock,
		RetrySleeper retrySleeper
	) {
		this.restClient = restClient;
		this.apiProperties = apiProperties;
		this.batchProperties = batchProperties;
		this.parser = parser;
		this.clock = clock;
		this.retrySleeper = retrySleeper;
	}

	@Override
	public KtoFetchedEnglishSyncPage fetchFetchedPage(int pageNumber) {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("KTO English page number must be at least 1");
		}
		if (apiProperties.serviceKey() == null || apiProperties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException("KTO service key is not configured");
		}
		for (int attempt = 1; attempt <= MAX_TRANSIENT_ATTEMPTS; attempt++) {
			try {
				return fetchOnce(pageNumber);
			} catch (KtoTransportException | KtoProviderException exception) {
				if (!retryable(exception) || attempt == MAX_TRANSIENT_ATTEMPTS) {
					throw exception;
				}
				pause(attempt);
			}
		}
		throw new IllegalStateException("KTO English retry attempts were exhausted");
	}

	private KtoFetchedEnglishSyncPage fetchOnce(int pageNumber) {
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
					var page = parser.parse(payload, batchProperties.pageSize());
					Instant receivedAt = Instant.now(clock);
					return new KtoFetchedEnglishSyncPage(
						page,
						new KtoSuccessfulCallMetadata(
							requestedAt,
							receivedAt,
							Duration.between(requestedAt, receivedAt).toMillis(),
							response.getStatusCode().value()),
						payload);
				});
		} catch (KtoProviderException | KtoResponseTooLargeException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new KtoTransportException();
		}
	}

	private boolean retryable(RuntimeException exception) {
		if (exception instanceof KtoTransportException) {
			return true;
		}
		if (exception instanceof KtoProviderException providerException) {
			String code = providerException.providerCode();
			return "22".equals(code) || "HTTP_429".equals(code) || code.matches("HTTP_5\\d\\d");
		}
		return false;
	}

	private void pause(int completedAttempt) {
		try {
			retrySleeper.sleep(INITIAL_RETRY_DELAY_MILLIS * (1L << (completedAttempt - 1)));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new KtoTransportException();
		}
	}

	private byte[] readBounded(InputStream input, int maxResponseBytes) throws IOException {
		var output = new ByteArrayOutputStream(Math.min(64 * 1024, maxResponseBytes));
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

	@FunctionalInterface
	interface RetrySleeper {
		void sleep(long delayMillis) throws InterruptedException;
	}
}
