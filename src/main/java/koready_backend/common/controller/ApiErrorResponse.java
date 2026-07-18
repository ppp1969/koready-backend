package koready_backend.common.controller;

import java.util.List;

public record ApiErrorResponse(
	boolean success,
	String code,
	String message,
	List<ApiFieldError> errors,
	String traceId
) {

	public ApiErrorResponse(String code, String message, String traceId) {
		this(false, code, message, List.of(), traceId);
	}

	public record ApiFieldError(String field, String reason) {
	}
}
