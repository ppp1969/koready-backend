package koready_backend.kto.infrastructure.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import koready_backend.kto.application.exception.KtoClientConfigurationException;
import koready_backend.kto.application.exception.KtoProviderException;
import koready_backend.kto.application.exception.KtoResponseTooLargeException;
import koready_backend.kto.application.exception.KtoTransportException;
import koready_backend.kto.application.port.KtoPhotoAwardClient;
import koready_backend.kto.domain.KtoPhotoAwardImage;
import koready_backend.kto.infrastructure.config.KtoApiProperties;

@Component
public final class KtoPhotoAwardApiClient implements KtoPhotoAwardClient {
	private static final int PAGE_SIZE = 200;
	private final RestClient restClient;
	private final KtoApiProperties properties;
	private final KtoPhotoAwardResponseParser parser;

	public KtoPhotoAwardApiClient(@Qualifier("ktoPhotoAwardRestClient") RestClient restClient,
		KtoApiProperties properties, KtoPhotoAwardResponseParser parser) {
		this.restClient = restClient;
		this.properties = properties;
		this.parser = parser;
	}

	@Override
	public List<KtoPhotoAwardImage> fetchAll() {
		if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
			throw new KtoClientConfigurationException("KTO service key is not configured");
		}
		List<KtoPhotoAwardImage> result = new ArrayList<>();
		for (int page = 1, last = 1; page <= last; page++) {
			KtoPhotoAwardResponseParser.Page parsed = fetch(page);
			if (parsed.pageNumber() != page) throw new KtoProviderException("INVALID_PAGE");
			result.addAll(parsed.images());
			last = parsed.totalCount() == 0 ? page
				: (parsed.totalCount() + parsed.pageSize() - 1) / parsed.pageSize();
		}
		return List.copyOf(result);
	}

	private KtoPhotoAwardResponseParser.Page fetch(int page) {
		try {
			return restClient.get().uri(builder -> builder.path("/phokoAwrdSyncList")
				.queryParam("numOfRows", PAGE_SIZE).queryParam("pageNo", page)
				.queryParam("MobileOS", properties.mobileOs())
				.queryParam("MobileApp", properties.mobileApp()).queryParam("_type", "json")
				.queryParam("serviceKey", properties.serviceKey()).build())
				.accept(MediaType.APPLICATION_JSON).exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful())
						throw KtoProviderException.forHttpStatus(response.getStatusCode().value());
					return parser.parse(readBounded(response.getBody()));
				});
		} catch (KtoProviderException | KtoResponseTooLargeException exception) { throw exception; }
		catch (RestClientException exception) { throw new KtoTransportException(); }
	}

	private byte[] readBounded(InputStream input) throws IOException {
		var output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192]; int total = 0; int read;
		while ((read = input.read(buffer)) != -1) {
			if (total + read > properties.maxResponseBytes())
				throw new KtoResponseTooLargeException(properties.maxResponseBytes());
			output.write(buffer, 0, read); total += read;
		}
		return output.toByteArray();
	}
}
