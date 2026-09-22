package koready_backend.kto.infrastructure.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import koready_backend.kto.application.*;
import koready_backend.kto.application.exception.KtoProviderException;

class KtoQuotaInterceptorTest {
	@Test
	void blocksBeforeNetworkIoAndCountsEveryAttemptIndependentlyPerOperation() {
		var counts = new ConcurrentHashMap<String, AtomicInteger>();
		var quota = new KtoRequestQuotaService((day, operation, limit) ->
			counts.computeIfAbsent(operation, key -> new AtomicInteger()).incrementAndGet() <= limit,
			new KtoRequestQuotaProperties(1, Map.of()));
		var builder = RestClient.builder().baseUrl("https://example.invalid/KorService2")
			.requestInterceptor(new KtoQuotaInterceptor("kor", quota));
		var server = MockRestServiceServer.bindTo(builder).build();
		server.expect(request -> {}).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
		server.expect(request -> {}).andRespond(withSuccess("image", MediaType.TEXT_PLAIN));
		var client = builder.build();
		assertEquals("ok", client.get().uri("/detailCommon2").retrieve().body(String.class));
		assertThrows(KtoProviderException.class,
			() -> client.get().uri("/detailCommon2").retrieve().body(String.class));
		assertEquals("image", client.get().uri("/detailImage2").retrieve().body(String.class));
		server.verify();
	}
}
