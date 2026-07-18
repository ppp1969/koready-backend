package koready_backend.common.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Trace-Id";
	private static final String ATTRIBUTE_NAME = TraceIdFilter.class.getName() + ".traceId";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String traceId = UUID.randomUUID().toString();
		request.setAttribute(ATTRIBUTE_NAME, traceId);
		response.setHeader(HEADER_NAME, traceId);
		filterChain.doFilter(request, response);
	}

	public static String current(HttpServletRequest request) {
		Object traceId = request.getAttribute(ATTRIBUTE_NAME);
		return traceId instanceof String value ? value : UUID.randomUUID().toString();
	}
}
