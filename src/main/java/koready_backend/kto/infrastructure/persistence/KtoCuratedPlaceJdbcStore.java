package koready_backend.kto.infrastructure.persistence;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import koready_backend.kto.application.port.KtoCuratedPlaceStore;
import koready_backend.kto.domain.KtoPlaceDetail;
import koready_backend.kto.domain.KtoPlaceImage;
import koready_backend.kto.domain.KtoPlaceItem;
import koready_backend.kto.domain.KtoPhotoAwardImage;
import koready_backend.onboarding.domain.InitialCandidatePlace;

@Repository
public class KtoCuratedPlaceJdbcStore implements KtoCuratedPlaceStore {

	private static final DateTimeFormatter KTO_TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final JdbcTemplate jdbcTemplate;

	public KtoCuratedPlaceJdbcStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public long upsert(InitialCandidatePlace specification, KtoPlaceDetail detail) {
		return upsert(specification, detail, List.of());
	}

	@Override
	@Transactional
	public long upsert(
		InitialCandidatePlace specification,
		KtoPlaceDetail detail,
		List<KtoPlaceImage> images
	) {
		return upsert(specification, detail, images, List.of());
	}

	@Override
	@Transactional
	public long upsert(
		InitialCandidatePlace specification,
		KtoPlaceDetail detail,
		List<KtoPlaceImage> images,
		List<KtoPhotoAwardImage> awardImages
	) {
		KtoPlaceItem item = detail.place();
		requirePinnedMetadata(specification, item);
		String serviceRegionCode = resolveServiceRegion(item);
		if (!specification.serviceRegionCode().name().equals(serviceRegionCode)) {
			throw new IllegalStateException(
				"KTO region does not match the approved region: " + specification.ktoContentId());
		}

		String address = required(joinAddress(item.address1(), item.address2()), 500, "address");
		BigDecimal latitude = coordinate(item.latitude(), -90, 90, "latitude");
		BigDecimal longitude = coordinate(item.longitude(), -180, 180, "longitude");
		String imageUrl = required(item.primaryImageUrl(), 1000, "primary image URL");
		String homepage = optional(detail.homepage(), 1000, "homepage");

		jdbcTemplate.update(
			"""
			INSERT INTO places
			    (kto_content_id, kto_content_type_id, service_region_code,
			     area_code, sigungu_code, ldong_regn_cd, ldong_signgu_cd,
			     lcls_systm1, lcls_systm2, lcls_systm3,
			     address, latitude, longitude, tel, homepage, first_image_url,
			     source_modified_time, show_flag, active, data_quality_score)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, TRUE, 100.00)
			ON DUPLICATE KEY UPDATE
			    kto_content_type_id = VALUES(kto_content_type_id),
			    service_region_code = VALUES(service_region_code),
			    area_code = VALUES(area_code),
			    sigungu_code = VALUES(sigungu_code),
			    ldong_regn_cd = VALUES(ldong_regn_cd),
			    ldong_signgu_cd = VALUES(ldong_signgu_cd),
			    lcls_systm1 = VALUES(lcls_systm1),
			    lcls_systm2 = VALUES(lcls_systm2),
			    lcls_systm3 = VALUES(lcls_systm3),
			    address = VALUES(address),
			    latitude = VALUES(latitude),
			    longitude = VALUES(longitude),
			    tel = VALUES(tel),
			    homepage = VALUES(homepage),
			    first_image_url = VALUES(first_image_url),
			    source_modified_time = VALUES(source_modified_time),
			    show_flag = TRUE,
			    active = TRUE,
			    data_quality_score = 100.00
			""",
			item.contentId(),
			item.contentTypeId(),
			serviceRegionCode,
			item.areaCode(),
			item.districtCode(),
			item.legalDongRegionCode(),
			item.legalDongDistrictCode(),
			item.classificationCode1(),
			item.classificationCode2(),
			item.classificationCode3(),
			address,
			latitude,
			longitude,
			optional(item.phoneNumber(), 255, "phone number"),
			homepage,
			imageUrl,
			modifiedTime(item.modifiedTime()));

		long placeId = jdbcTemplate.queryForObject(
			"SELECT id FROM places WHERE kto_content_id = ?",
			Long.class,
			item.contentId());
		upsertKoreanLocalization(placeId, item, detail, address);
		upsertEnglishLocalization(placeId, specification, item.contentId(), address);
		upsertApprovedStyle(placeId, specification);
		replaceImages(placeId, item, images, awardImages);
		return placeId;
	}

	private void replaceImages(
		long placeId,
		KtoPlaceItem item,
		List<KtoPlaceImage> collectedImages,
		List<KtoPhotoAwardImage> collectedAwards
	) {
		LinkedHashMap<String, KtoPhotoAwardImage> awards = new LinkedHashMap<>();
		for (KtoPhotoAwardImage image : collectedAwards == null
			? List.<KtoPhotoAwardImage>of() : collectedAwards) {
			if (image.visible()) awards.putIfAbsent(image.originImageUrl(), image);
		}
		KtoPlaceImage representativeImage = representativeImage(item, awards.keySet());
		List<KtoPlaceImage> detailImages = detailImages(
			collectedImages,
			awards.keySet(),
			representativeImage == null ? null : representativeImage.originImageUrl());
		if (awards.size() + (representativeImage == null ? 0 : 1) + detailImages.size() < 4) {
			throw new IllegalStateException("Approved KTO place requires four distinct images: " + item.contentId());
		}
		jdbcTemplate.update(
			"DELETE FROM place_images WHERE place_id = ? AND source_type IN ('KTO_DETAIL', 'KTO_PHOTO_AWARD')",
			placeId);
		int remaining = 4;
		for (KtoPhotoAwardImage image : awards.values()) {
			if (remaining <= 0) break;
			remaining--;
			jdbcTemplate.update("""
				INSERT INTO place_images
				    (place_id, image_url, thumbnail_image_url, image_url_sha256,
				     source_type, source_priority, source_order, source_content_id,
				     source_image_name, copyright_type)
				VALUES (?, ?, ?, ?, 'KTO_PHOTO_AWARD', 300, ?, ?, ?, ?)
				""", placeId, image.originImageUrl(), image.thumbnailImageUrl(),
				sha256(image.originImageUrl()), image.sourceOrder(), image.contentId(),
				optional(image.title(), 500, "award image title"),
				optional(image.copyrightType(), 30, "award copyright type"));
		}
		if (remaining > 0 && representativeImage != null) {
			remaining--;
			insertKtoImage(placeId, item, representativeImage, 200);
		}
		for (KtoPlaceImage image : detailImages) {
			if (remaining <= 0) break;
			remaining--;
			insertKtoImage(placeId, item, image, 100);
		}
	}

	private void insertKtoImage(long placeId, KtoPlaceItem item, KtoPlaceImage image, int priority) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_images
			    (place_id, image_url, thumbnail_image_url, image_url_sha256,
			     source_type, source_priority, source_order, source_content_id,
			     source_image_name, copyright_type)
			VALUES (?, ?, ?, ?, 'KTO_DETAIL', ?, ?, ?, ?, ?)
			""",
			placeId,
			image.originImageUrl(),
			image.thumbnailImageUrl(),
			sha256(image.originImageUrl()),
			priority,
			image.sourceOrder(),
			item.contentId(),
			optional(image.imageName(), 500, "image name"),
			optional(image.copyrightType(), 30, "image copyright type"));
	}

	private KtoPlaceImage representativeImage(KtoPlaceItem item, java.util.Set<String> excludedUrls) {
		if (item.primaryImageUrl() == null || item.primaryImageUrl().isBlank()
			|| excludedUrls.contains(item.primaryImageUrl())) {
			return null;
		}
		return new KtoPlaceImage(
			item.primaryImageUrl(),
			item.thumbnailImageUrl(),
			item.title(),
			item.copyrightType(),
			1);
	}

	private List<KtoPlaceImage> detailImages(
		List<KtoPlaceImage> collectedImages,
		java.util.Set<String> excludedUrls,
		String representativeImageUrl
	) {
		LinkedHashMap<String, KtoPlaceImage> unique = new LinkedHashMap<>();
		for (KtoPlaceImage image : collectedImages == null ? List.<KtoPlaceImage>of() : collectedImages) {
			if (!excludedUrls.contains(image.originImageUrl())
				&& !image.originImageUrl().equals(representativeImageUrl)) {
				unique.putIfAbsent(image.originImageUrl(), image);
			}
		}
		return new ArrayList<>(unique.values()).stream().limit(4).toList();
	}

	private void upsertKoreanLocalization(
		long placeId,
		KtoPlaceItem item,
		KtoPlaceDetail detail,
		String address
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text,
			     translation_source, source_content_id, source_hash)
			VALUES (?, 'KO', ?, ?, ?, 'KTO_KO', ?, ?)
			ON DUPLICATE KEY UPDATE
			    title = VALUES(title),
			    overview = VALUES(overview),
			    address_text = VALUES(address_text),
			    translation_source = 'KTO_KO',
			    source_content_id = VALUES(source_content_id),
			    source_hash = VALUES(source_hash)
			""",
			placeId,
			item.title(),
			detail.overview(),
			address,
			item.contentId(),
			item.sourceHash());
	}

	private void upsertEnglishLocalization(
		long placeId,
		InitialCandidatePlace specification,
		String contentId,
		String address
	) {
		jdbcTemplate.update(
			"""
			INSERT INTO place_localizations
			    (place_id, language, title, overview, address_text,
			     translation_source, source_content_id, source_hash)
			VALUES (?, 'EN', ?, NULL, ?, 'MANUAL_EDITED', ?, NULL)
			ON DUPLICATE KEY UPDATE
			    title = VALUES(title),
			    overview = NULL,
			    address_text = VALUES(address_text),
			    translation_source = 'MANUAL_EDITED',
			    source_content_id = VALUES(source_content_id),
			    source_hash = NULL
			""",
			placeId,
			specification.titleEn(),
			address,
			contentId);
	}

	private void upsertApprovedStyle(long placeId, InitialCandidatePlace specification) {
		jdbcTemplate.update(
			"""
			UPDATE place_style_mappings
			SET confidence = LEAST(confidence, 0.9999)
			WHERE place_id = ? AND travel_style <> ?
			""",
			placeId,
			specification.travelStyle().name());
		jdbcTemplate.update(
			"""
			INSERT INTO place_style_mappings
			    (place_id, travel_style, source, confidence)
			VALUES (?, ?, 'MANUAL', 1.0000)
			ON DUPLICATE KEY UPDATE
			    source = 'MANUAL',
			    confidence = 1.0000
			""",
			placeId,
			specification.travelStyle().name());
	}

	private String resolveServiceRegion(KtoPlaceItem item) {
		String serviceRegion = region("KTO", item.areaCode());
		if (serviceRegion != null) {
			return serviceRegion;
		}
		String legalCode = item.legalDongRegionCode();
		serviceRegion = region("KTO_LDONG", legalCode);
		if (serviceRegion == null && legalCode != null && legalCode.length() > 2) {
			serviceRegion = region("KTO_LDONG", legalCode.substring(0, 2));
		}
		if (serviceRegion == null) {
			throw new IllegalStateException(
				"KTO place region could not be mapped: " + item.contentId());
		}
		return serviceRegion;
	}

	private String region(String provider, String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		try {
			return jdbcTemplate.queryForObject(
				"""
				SELECT service_region_code
				FROM administrative_regions
				WHERE provider = ? AND level = 'SIDO' AND parent_code = '' AND code = ?
				""",
				String.class,
				provider,
				code);
		} catch (EmptyResultDataAccessException exception) {
			return null;
		}
	}

	private void requirePinnedMetadata(
		InitialCandidatePlace specification,
		KtoPlaceItem item
	) {
		if (!specification.ktoContentId().equals(item.contentId())
			|| !specification.ktoContentTypeId().equals(item.contentTypeId())
			|| !specification.expectedKtoTitleKo().equals(item.title())) {
			throw new IllegalStateException(
				"KTO detail does not match the approved catalog: " + specification.ktoContentId());
		}
	}

	private String joinAddress(String address1, String address2) {
		if (address1 == null || address1.isBlank()) {
			return address2;
		}
		if (address2 == null || address2.isBlank()) {
			return address1;
		}
		return address1.strip() + " " + address2.strip();
	}

	private BigDecimal coordinate(String value, int minimum, int maximum, String field) {
		try {
			BigDecimal coordinate = new BigDecimal(required(value, 50, field));
			if (coordinate.compareTo(BigDecimal.valueOf(minimum)) < 0
				|| coordinate.compareTo(BigDecimal.valueOf(maximum)) > 0) {
				throw new IllegalStateException("KTO curated " + field + " is outside its valid range");
			}
			return coordinate;
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("KTO curated " + field + " is invalid");
		}
	}

	private LocalDateTime modifiedTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(value, KTO_TIMESTAMP);
		} catch (DateTimeParseException exception) {
			throw new IllegalStateException("KTO curated modified time is invalid");
		}
	}

	private String required(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("KTO curated " + field + " is required");
		}
		String normalized = value.strip();
		if (normalized.length() > maxLength) {
			throw new IllegalStateException("KTO curated " + field + " is too long");
		}
		return normalized;
	}

	private String optional(String value, int maxLength, String field) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return required(value, maxLength, field);
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
