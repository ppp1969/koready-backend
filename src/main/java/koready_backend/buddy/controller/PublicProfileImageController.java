package koready_backend.buddy.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import koready_backend.buddy.application.ProfileImageService;

@RestController
@RequestMapping("/api/v1/profile-images")
public class PublicProfileImageController {

	private final ProfileImageService service;

	public PublicProfileImageController(ProfileImageService service) {
		this.service = service;
	}

	@GetMapping("/{imageId:img_[0-9a-f]{32}}")
	public ResponseEntity<Void> view(
		@PathVariable String imageId,
		Authentication authentication
	) {
		String viewerPublicId = authentication == null
			? null
			: authentication.getName();
		String signedUrl = service.viewUrl(imageId, viewerPublicId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
			.location(URI.create(signedUrl))
			.build();
	}
}
