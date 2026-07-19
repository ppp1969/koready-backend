package koready_backend.location.infrastructure.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import koready_backend.location.application.exception.ExpiredLocationSearchTokenException;
import koready_backend.location.application.exception.InvalidLocationSearchTokenException;
import koready_backend.location.application.port.LocationSearchTokenCodec;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.location.infrastructure.config.LocationSearchProperties;
import koready_backend.place.domain.ServiceRegionCode;

class HmacLocationSearchTokenCodecTest {

	private static final Instant NOW = Instant.parse("2026-07-19T06:00:00Z");
	private static final String SECRET = "test-location-token-secret-at-least-32-bytes";

	@Test
	void issuesAndVerifiesATenMinuteSignedToken() {
		var codec = codec(Clock.fixed(NOW, ZoneOffset.UTC));
		String token = codec.issue(candidate(), ServiceRegionCode.SEOUL);
		var verified = codec.verify(token);

		assertTrue(token.startsWith("locsrch_"));
		assertEquals(candidate(), verified.candidate());
		assertEquals(ServiceRegionCode.SEOUL, verified.serviceRegionCode());
		assertEquals(NOW.plus(Duration.ofMinutes(10)), verified.expiresAt());
	}

	@Test
	void rejectsTamperedAndExpiredTokens() {
		var codec = codec(Clock.fixed(NOW, ZoneOffset.UTC));
		var shortLived = new HmacLocationSearchTokenCodec(
			new LocationSearchProperties("local", SECRET, Duration.ofSeconds(1)),
			JsonMapper.builder().build(),
			Clock.fixed(NOW, ZoneOffset.UTC));
		String token = shortLived.issue(candidate(), ServiceRegionCode.SEOUL);

		assertThrows(InvalidLocationSearchTokenException.class,
			() -> codec.verify(token.substring(0, token.length() - 1) + "x"));

		var later = codec(Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
		assertThrows(ExpiredLocationSearchTokenException.class,
			() -> later.verify(token));
	}

	private static HmacLocationSearchTokenCodec codec(Clock clock) {
		return new HmacLocationSearchTokenCodec(
			new LocationSearchProperties("local", SECRET, Duration.ofMinutes(10)),
			JsonMapper.builder().build(),
			clock);
	}

	private static LocationSearchCandidate candidate() {
		return new LocationSearchCandidate(
			LocationSearchResultType.PLACE,
			"123456789",
			"성신여자대학교",
			"서울특별시 성북구 보문로34다길 2",
			"서울특별시 성북구 돈암동 173-1",
			37.5928,
			127.0165,
			"서울특별시",
			"성북구",
			"돈암동");
	}
}
