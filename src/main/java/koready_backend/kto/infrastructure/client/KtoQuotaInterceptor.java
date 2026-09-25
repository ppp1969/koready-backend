package koready_backend.kto.infrastructure.client;

import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import koready_backend.kto.application.KtoRequestQuotaService;

public record KtoQuotaInterceptor(String api, KtoRequestQuotaService quota)
	implements ClientHttpRequestInterceptor {
	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
		throws IOException {
		String path = request.getURI().getPath();
		String operation = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		quota.reserve(api + "-" + operation);
		return execution.execute(request, body);
	}
}
