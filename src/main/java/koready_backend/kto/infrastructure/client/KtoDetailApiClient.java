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
import koready_backend.kto.application.model.KtoFetchedDetailOperation;
import koready_backend.kto.application.model.KtoSuccessfulCallMetadata;
import koready_backend.kto.application.port.KtoDetailClient;
import koready_backend.kto.domain.KtoDetailOperation;
import koready_backend.kto.domain.KtoDetailTarget;
import koready_backend.kto.infrastructure.config.KtoApiProperties;

@Component
public final class KtoDetailApiClient implements KtoDetailClient {

	private static final int READ_BUFFER_BYTES = 8 * 1024;
	private static final int MAX_TRANSIENT_ATTEMPTS = 4;
	private static final long INITIAL_RETRY_DELAY_MILLIS = 1_000;

	private final RestClient restClient;
	private final KtoApiProperties apiProperties;
	private final KtoDetailResponseParser parser;
	private final Clock clock;
	private final RetrySleeper retrySleeper;

	@Autowired
	public KtoDetailApiClient(
		@Qualifier("ktoRestClient") RestClient restClient,
		KtoApiProperties apiProperties,
		KtoDetailResponseParser parser
	) {
		this(restClient, apiProperties, parser, Clock.systemUTC(), Thread::sleep);
	}

	KtoDetailApiClient(
		RestClient restClient,
		KtoApiProperties apiProperties,
		KtoDetailResponseParser parser,
		Clock clock,
		RetrySleeper retrySleeper
	) {
		this.restClient = restClient;
		this.apiProperties = apiProperties;
		this.parser = parser;
		this.clock = clock;
		this.retrySleeper = retrySleeper;
	}

	@Override
	public KtoFetchedDetailOperation fetch(
		KtoDetailOperation operation,
		KtoDetailTarget target
	) {
		requireServiceKey();
		for (int attempt = 1; attempt <= MAX_TRANSIENT_ATTEMPTS; attempt++) {
			try {
				return fetchOnce(operation, target);
			} catch (KtoTransportException | KtoProviderException exception) {
				if (!retryable(exception) || attempt == MAX_TRANSIENT_ATTEMPTS) {
					throw exception;
				}
				pause(attempt);
			}
		}
		throw new IllegalStateException("KTO detail retry attempts were exhausted");
	}

	private KtoFetchedDetailOperation fetchOnce(
		KtoDetailOperation operation,
		KtoDetailTarget target
	) {
		try {
			Instant requestedAt = Instant.now(clock);
			return restClient.get()
				.uri(uriBuilder -> {
					var builder = uriBuilder
						.path("/" + operation.apiName())
						.queryParam("MobileOS", apiProperties.mobileOs())
						.queryParam("MobileApp", apiProperties.mobileApp())
						.queryParam("_type", "json")
						.queryParam("contentId", target.contentId());
					if (operation.contentTypeRequired()) {
						builder.queryParam("contentTypeId", target.contentTypeId());
					}
					if (operation == KtoDetailOperation.INFO
						|| operation == KtoDetailOperation.IMAGE) {
						builder.queryParam("numOfRows", 100);
						builder.queryParam("pageNo", 1);
					}
					return builder
						.queryParam("serviceKey", apiProperties.serviceKey())
						.build();
				})
				.accept(MediaType.APPLICATION_JSON)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw KtoProviderException.forHttpStatus(
							response.getStatusCode().value());
					}
					long contentLength = response.getHeaders().getContentLength();
					if (contentLength > apiProperties.maxResponseBytes()) {
						throw new KtoResponseTooLargeException(
							apiProperties.maxResponseBytes());
					}
					byte[] payload = readBounded(
						response.getBody(), apiProperties.maxResponseBytes());
					var parsed = parser.parse(operation, payload);
					Instant receivedAt = Instant.now(clock);
					return new KtoFetchedDetailOperation(
						parsed,
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

	private void requireServiceKey() {
		if (apiProperties.serviceKey() == null || apiProperties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException("KTO service key is not configured");
		}
	}

	private boolean retryable(RuntimeException exception) {
		if (exception instanceof KtoTransportException) {
			return true;
		}
		if (exception instanceof KtoProviderException providerException) {
			String code = providerException.providerCode();
			return code.matches("HTTP_5\\d\\d");
		}
		return false;
	}

	private void pause(int completedAttempt) {
		try {
			retrySleeper.sleep(
				INITIAL_RETRY_DELAY_MILLIS * (1L << (completedAttempt - 1)));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new KtoTransportException();
		}
	}

	private byte[] readBounded(InputStream input, int maxResponseBytes)
		throws IOException {
		var output = new ByteArrayOutputStream(
			Math.min(64 * 1024, maxResponseBytes));
		byte[] buffer = new byte[READ_BUFFER_BYTES];
		int totalBytes = 0;
		while (true) {
			int bytesToRead = Math.min(
				buffer.length, maxResponseBytes - totalBytes + 1);
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
