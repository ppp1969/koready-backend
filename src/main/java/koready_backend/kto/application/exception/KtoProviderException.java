package koready_backend.kto.application.exception;

public final class KtoProviderException extends RuntimeException {

	private final String providerCode;

	public KtoProviderException(String providerCode) {
		super("KTO provider rejected the request (code=" + safeCode(providerCode) + ")");
		this.providerCode = safeCode(providerCode);
	}

	public static KtoProviderException forHttpStatus(int statusCode) {
		return new KtoProviderException("HTTP_" + statusCode);
	}

	public String providerCode() {
		return providerCode;
	}

	private static String safeCode(String providerCode) {
		if (providerCode == null || providerCode.isBlank()) {
			return "UNKNOWN";
		}
		return providerCode.replaceAll("[^A-Za-z0-9_-]", "_");
	}
}
