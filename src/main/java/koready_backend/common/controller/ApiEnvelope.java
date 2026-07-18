package koready_backend.common.controller;

public record ApiEnvelope<T>(
	boolean success,
	String code,
	String message,
	T data,
	String traceId
) {

	public static <T> ApiEnvelope<T> success(String code, T data, String traceId) {
		return new ApiEnvelope<>(true, code, "OK", data, traceId);
	}
}
