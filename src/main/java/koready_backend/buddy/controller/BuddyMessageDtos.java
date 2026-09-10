package koready_backend.buddy.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import koready_backend.buddy.application.BuddyMessageQueryService;
import koready_backend.buddy.application.BuddyMessageService;
import koready_backend.place.domain.PlaceLanguage;

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

	record ThreadListResponse(
		List<ThreadSummary> items,
		String nextCursor,
		boolean hasMore,
		long unreadTotal
	) {
		static ThreadListResponse from(
			BuddyMessageQueryService.ThreadListResult result,
			PlaceLanguage language
		) {
			return new ThreadListResponse(
				result.items().stream().map(item -> ThreadSummary.from(item, language)).toList(),
				result.nextCursor(),
				result.hasMore(),
				result.unreadTotal());
		}
	}

	record ThreadSummary(
		String threadId,
		PlaceSummary place,
		ProfileSummary otherProfile,
		String preview,
		Instant lastSentAt,
		long unreadCount,
		boolean blocked,
		boolean canReply
	) {
		static ThreadSummary from(
			BuddyMessageQueryService.ThreadSummary summary,
			PlaceLanguage language
		) {
			return new ThreadSummary(
				summary.threadId(),
				PlaceSummary.from(summary.place()),
				ProfileSummary.from(summary.otherProfile(), language),
				summary.preview(),
				summary.lastSentAt(),
				summary.unreadCount(),
				summary.blocked(),
				summary.canReply());
		}
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
		static ThreadResponse from(
			BuddyMessageService.ThreadResult result,
			PlaceLanguage language
		) {
			return new ThreadResponse(
				result.threadId(),
				PlaceSummary.from(result.place()),
				ProfileSummary.from(result.otherProfile(), language),
				result.messages().stream().map(MessageResponse::from).toList(),
				result.nextCursor(),
				result.hasMore(),
				result.canReply());
		}
	}

	record PlaceSummary(long placeId, String title, String imageUrl) {
		static PlaceSummary from(BuddyMessageService.PlaceSummary summary) {
			return new PlaceSummary(
				summary.placeId(), summary.title(), summary.imageUrl());
		}
	}

	record ProfileSummary(
		long profileId,
		String nickname,
		String profileImageUrl,
		String nationalityCode,
		String nationalityName
	) {
		static ProfileSummary from(
			BuddyMessageService.ProfileSummary summary,
			PlaceLanguage language
		) {
			return new ProfileSummary(
				summary.profileId(), summary.nickname(), summary.profileImageUrl(),
				summary.nationalityCode(),
				CountryDisplayName.of(summary.nationalityCode(), language));
		}
	}

	record ReadResponse(
		String threadId,
		Instant readAt,
		long threadUnreadCount,
		long unreadTotal
	) {
		static ReadResponse from(BuddyMessageQueryService.ReadResult result) {
			return new ReadResponse(
				result.threadId(),
				result.readAt(),
				result.threadUnreadCount(),
				result.unreadTotal());
		}
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
