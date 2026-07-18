package koready_backend.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import koready_backend.common.controller.ApiErrorResponse;
import koready_backend.common.controller.TraceIdFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class ApiSecurityErrorHandler
	implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	public ApiSecurityErrorHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException {
		write(
			request,
			response,
			HttpStatus.UNAUTHORIZED,
			"UNAUTHORIZED",
			"Authentication is required.");
	}

	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		AccessDeniedException exception
	) throws IOException {
		boolean adminPath = request.getRequestURI().startsWith("/api/v1/admin/");
		write(
			request,
			response,
			HttpStatus.FORBIDDEN,
			adminPath ? "ADMIN_FORBIDDEN" : "FORBIDDEN",
			"The authenticated account does not have permission.");
	}

	private void write(
		HttpServletRequest request,
		HttpServletResponse response,
		HttpStatus status,
		String code,
		String message
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(
			response.getOutputStream(),
			new ApiErrorResponse(code, message, TraceIdFilter.current(request)));
	}
}
