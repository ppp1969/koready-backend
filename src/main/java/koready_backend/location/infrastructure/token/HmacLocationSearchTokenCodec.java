package koready_backend.location.infrastructure.token;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import koready_backend.location.application.exception.ExpiredLocationSearchTokenException;
import koready_backend.location.application.exception.InvalidLocationSearchTokenException;
import koready_backend.location.application.exception.LocationProviderUnavailableException;
import koready_backend.location.application.port.LocationSearchTokenCodec;
import koready_backend.location.domain.LocationSearchCandidate;
import koready_backend.location.domain.LocationSearchResultType;
import koready_backend.location.infrastructure.config.LocationSearchProperties;
import koready_backend.place.domain.ServiceRegionCode;

@Component
public final class HmacLocationSearchTokenCodec implements LocationSearchTokenCodec {

	private static final String PREFIX = "locsrch_";
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int MIN_SECRET_BYTES = 32;
	private static final int MAX_TOKEN_LENGTH = 8 * 1024;

	private final LocationSearchProperties properties;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	@Autowired
	public HmacLocationSearchTokenCodec(
		LocationSearchProperties properties,
		JsonMapper jsonMapper
	) {
		this(properties, jsonMapper, Clock.systemUTC());
	}

	HmacLocationSearchTokenCodec(
		LocationSearchProperties properties,
		JsonMapper jsonMapper,
		Clock clock
	) {
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	public String issue(
		LocationSearchCandidate candidate,
		ServiceRegionCode serviceRegionCode
	) {
		ensureConfigured();
		Instant expiresAt = clock.instant().plus(properties.tokenTtl());
		var payload = SignedPayload.from(candidate, serviceRegionCode, expiresAt);
		try {
			String encodedPayload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(jsonMapper.writeValueAsBytes(payload));
			return PREFIX + encodedPayload + '.' + signature(encodedPayload);
		} catch (LocationProviderUnavailableException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new LocationProviderUnavailableException();
		}
	}

	@Override
	public TokenPayload verify(String token) {
		ensureConfigured();
		if (token == null || token.length() > MAX_TOKEN_LENGTH
			|| !token.startsWith(PREFIX)) {
			throw new InvalidLocationSearchTokenException();
		}
		String value = token.substring(PREFIX.length());
		int separator = value.indexOf('.');
		if (separator < 1 || separator != value.lastIndexOf('.')) {
			throw new InvalidLocationSearchTokenException();
		}
		String encodedPayload = value.substring(0, separator);
		String encodedSignature = value.substring(separator + 1);
		if (!validSignature(encodedPayload, encodedSignature)) {
			throw new InvalidLocationSearchTokenException();
		}

		try {
			byte[] json = Base64.getUrlDecoder().decode(encodedPayload);
			SignedPayload signed = jsonMapper.readValue(json, SignedPayload.class);
			TokenPayload payload = signed.toTokenPayload();
			if (!payload.expiresAt().isAfter(clock.instant())) {
				throw new ExpiredLocationSearchTokenException();
			}
			return payload;
		} catch (ExpiredLocationSearchTokenException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new InvalidLocationSearchTokenException();
		}
	}

	private boolean validSignature(String payload, String encodedSignature) {
		try {
			byte[] expected = Base64.getUrlDecoder().decode(signature(payload));
			byte[] actual = Base64.getUrlDecoder().decode(encodedSignature);
			return MessageDigest.isEqual(expected, actual);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private String signature(String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(
				properties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
		} catch (GeneralSecurityException exception) {
			throw new LocationProviderUnavailableException();
		}
	}

	private void ensureConfigured() {
		if (properties.tokenSecret().getBytes(StandardCharsets.UTF_8).length
			< MIN_SECRET_BYTES) {
			throw new LocationProviderUnavailableException();
		}
	}

	private record SignedPayload(
		int version,
		LocationSearchResultType resultType,
		String providerPlaceId,
		String name,
		String roadAddress,
		String address,
		double latitude,
		double longitude,
		String sido,
		String sigungu,
		String dong,
		ServiceRegionCode serviceRegionCode,
		Instant expiresAt
	) {

		private static SignedPayload from(
			LocationSearchCandidate candidate,
			ServiceRegionCode serviceRegionCode,
			Instant expiresAt
		) {
			return new SignedPayload(
				1,
				candidate.resultType(),
				candidate.providerPlaceId(),
				candidate.name(),
				candidate.roadAddress(),
				candidate.address(),
				candidate.latitude(),
				candidate.longitude(),
				candidate.sido(),
				candidate.sigungu(),
				candidate.dong(),
				serviceRegionCode,
				expiresAt);
		}

		private TokenPayload toTokenPayload() {
			if (version != 1) {
				throw new InvalidLocationSearchTokenException();
			}
			return new TokenPayload(
				new LocationSearchCandidate(
					resultType,
					providerPlaceId,
					name,
					roadAddress,
					address,
					latitude,
					longitude,
					sido,
					sigungu,
					dong),
				serviceRegionCode,
				expiresAt);
		}
	}
}
