package koready_backend.buddy.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import koready_backend.buddy.application.ProfileImageService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1/users/me/profile-image")
public class ProfileImageController {

	private final ProfileImageService service;

	public ProfileImageController(ProfileImageService service) {
		this.service = service;
	}

	@PostMapping("/upload-url")
	public ApiEnvelope<UploadReservationResponse> reserve(
		@Valid @RequestBody UploadReservationRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var result = service.reserve(
			authentication.getName(), body.contentType(), body.size());
		return ApiEnvelope.success(
			"PROFILE_IMAGE_UPLOAD_RESERVED",
			new UploadReservationResponse(
				result.imageId(),
				result.uploadUrl(),
				result.expiresAt(),
				result.requiredHeaders()),
			TraceIdFilter.current(request));
	}

	@PostMapping("/complete")
	public ApiEnvelope<CompletedImageResponse> complete(
		@Valid @RequestBody CompleteImageRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		var result = service.complete(authentication.getName(), body.imageId());
		return ApiEnvelope.success(
			"PROFILE_IMAGE_UPLOAD_COMPLETED",
			new CompletedImageResponse(
				result.imageId(),
				result.profileImageUrl(),
				result.size(),
				result.completedAt()),
			TraceIdFilter.current(request));
	}

	record UploadReservationRequest(
		@NotBlank
		@Pattern(regexp = "image/(jpeg|png|webp)")
		String contentType,
		@Min(1) @Max(ProfileImageService.MAX_IMAGE_BYTES) long size
	) {
	}

	record UploadReservationResponse(
		String imageId,
		String uploadUrl,
		Instant expiresAt,
		Map<String, String> requiredHeaders
	) {
	}

	record CompleteImageRequest(
		@NotBlank
		@Pattern(regexp = "img_[0-9a-f]{32}")
		String imageId
	) {
	}

	record CompletedImageResponse(
		String imageId,
		String profileImageUrl,
		long size,
		Instant completedAt
	) {
	}
}
