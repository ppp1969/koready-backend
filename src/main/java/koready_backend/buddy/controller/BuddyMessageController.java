package koready_backend.buddy.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import koready_backend.buddy.application.BuddyMessageQueryService;
import koready_backend.buddy.application.BuddyMessageService;
import koready_backend.common.controller.ApiEnvelope;
import koready_backend.common.controller.TraceIdFilter;

@RestController
@RequestMapping("/api/v1")
@Validated
public class BuddyMessageController {

	private final BuddyMessageService commandService;
	private final BuddyMessageQueryService queryService;

	public BuddyMessageController(
		BuddyMessageService commandService,
		BuddyMessageQueryService queryService
	) {
		this.commandService = commandService;
		this.queryService = queryService;
	}

	@GetMapping("/message-threads")
	public ApiEnvelope<BuddyMessageDtos.ThreadListResponse> getThreads(
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"MESSAGE_THREADS_OK",
			BuddyMessageDtos.ThreadListResponse.from(queryService.getThreads(
				authentication.getName(), cursor, size)),
			TraceIdFilter.current(request));
	}

	@GetMapping("/message-threads/{threadId}")
	public ApiEnvelope<BuddyMessageDtos.ThreadResponse> getThread(
		@PathVariable String threadId,
		@RequestParam(required = false) @Size(max = 512) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"MESSAGE_THREAD_OK",
			BuddyMessageDtos.ThreadResponse.from(queryService.getThread(
				authentication.getName(), threadId, cursor, size)),
			TraceIdFilter.current(request));
	}

	@PutMapping("/message-threads/{threadId}/read")
	public ApiEnvelope<BuddyMessageDtos.ReadResponse> markRead(
		@PathVariable String threadId,
		Authentication authentication,
		HttpServletRequest request
	) {
		return ApiEnvelope.success(
			"MESSAGE_THREAD_READ",
			BuddyMessageDtos.ReadResponse.from(queryService.markRead(
				authentication.getName(), threadId)),
			TraceIdFilter.current(request));
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
			BuddyMessageDtos.ThreadResponse.from(commandService.createThread(
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
			BuddyMessageDtos.MessageResponse.from(commandService.reply(
				authentication.getName(), threadId, idempotencyKey, body.content())),
			TraceIdFilter.current(request));
	}
}
