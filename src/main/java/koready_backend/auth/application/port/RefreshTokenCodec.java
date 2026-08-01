package koready_backend.auth.application.port;

public interface RefreshTokenCodec {

	String generate();

	String hash(String value);
}
