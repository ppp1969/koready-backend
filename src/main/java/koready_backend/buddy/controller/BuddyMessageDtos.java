package koready_backend.buddy.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import koready_backend.buddy.application.BuddyMessageService;

final class BuddyMessageDtos {

	private BuddyMessageDtos() {
	}

	record CreateThreadRequest(
		@Positive long receiverProfileId,
		@Positive long placeId,
		@NotNull String content
	) {
		BuddyMessageService.CreateThreadCommand toCommand() {
			return new BuddyMessageService.CreateThreadCommand(
				receiverProfileId, placeId, content);
		}
	}

	record CreateMessageRequest(@NotNull String content) {
	}

	record ThreadResponse(
		String threadId,
		PlaceSummary place,
		ProfileSummary otherProfile,
		List<MessageResponse> messages,
		String nextCursor,
		boolean hasMore,
		boolean canReply
	) {
		static ThreadResponse from(BuddyMessageService.ThreadResult result) {
			return new ThreadResponse(
				result.threadId(),
				new PlaceSummary(
					result.place().placeId(),
					result.place().title(),
					result.place().imageUrl()),
				new ProfileSummary(
					result.otherProfile().profileId(),
					result.otherProfile().nickname(),
					result.otherProfile().profileImageUrl()),
				result.messages().stream().map(MessageResponse::from).toList(),
				result.nextCursor(),
				result.hasMore(),
				result.canReply());
		}
	}

	record PlaceSummary(long placeId, String title, String imageUrl) {
	}

	record ProfileSummary(long profileId, String nickname, String profileImageUrl) {
	}

	record MessageResponse(
		long messageId,
		String threadId,
		long senderProfileId,
		long receiverProfileId,
		long placeId,
		String content,
		Instant sentAt,
		boolean read,
		Instant readAt
	) {
		static MessageResponse from(BuddyMessageService.MessageResult result) {
			return new MessageResponse(
				result.messageId(),
				result.threadId(),
				result.senderProfileId(),
				result.receiverProfileId(),
				result.placeId(),
				result.content(),
				result.sentAt(),
				result.read(),
				result.readAt());
		}
	}
}
