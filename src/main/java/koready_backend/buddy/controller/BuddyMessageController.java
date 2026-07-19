package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import koready_backend.buddy.application.BuddyMessageService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1")
public class BuddyMessageController {

	private final BuddyMessageService service;

	public BuddyMessageController(BuddyMessageService service) {
		this.service = service;
	}

	@PostMapping("/message-threads")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiEnvelope<BuddyMessageDtos.ThreadResponse> createThread(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid BuddyMessageDtos.CreateThreadRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"MESSAGE_THREAD_CREATED",
			BuddyMessageDtos.ThreadResponse.from(service.createThread(
				authentication.getName(), idempotencyKey, body.toCommand())),
			TraceIdFilter.current(request));
	}

	@PostMapping("/message-threads/{threadId}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiEnvelope<BuddyMessageDtos.MessageResponse> reply(
		@PathVariable String threadId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid BuddyMessageDtos.CreateMessageRequest body,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"MESSAGE_CREATED",
			BuddyMessageDtos.MessageResponse.from(service.reply(
				authentication.getName(), threadId, idempotencyKey, body.content())),
			TraceIdFilter.current(request));
	}
}
