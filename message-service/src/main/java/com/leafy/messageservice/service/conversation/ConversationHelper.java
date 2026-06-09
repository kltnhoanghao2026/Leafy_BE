package com.leafy.messageservice.service.conversation;


import com.leafy.common.utils.PhoneUtil;
import com.leafy.common.utils.S3UtilV2;
import com.leafy.common.utils.SecurityUtil;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.enums.SocketEventType;
import com.leafy.common.dto.client.socketservice.SocketEvent;
import com.leafy.messageservice.dto.response.*;
import com.leafy.messageservice.model.ChatUser;
import com.leafy.messageservice.model.Conversation;
import com.leafy.messageservice.model.ConversationMember;
import com.leafy.messageservice.model.GroupSettings;
import com.leafy.messageservice.model.LastMessageInfo;
import com.leafy.messageservice.model.Message;
import com.leafy.messageservice.model.enums.JoinRequestStatus;
import com.leafy.messageservice.model.enums.MemberRole;
import com.leafy.messageservice.repository.ChatUserRepository;
import com.leafy.messageservice.repository.ConversationRepository;
import com.leafy.messageservice.repository.JoinRequestRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared helpers used by both ConversationServiceImpl and GroupConversationServiceImpl.
 */
@Component
@RequiredArgsConstructor
@Getter
public class ConversationHelper {

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final SecurityUtil securityUtil;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MongoTemplate mongoTemplate;
    private final S3UtilV2 s3UtilV2;
    private final KafkaTopicProperties kafkaTopicProperties;

    public record ActorInfo(String name, String avatar) {
        public static ActorInfo of(ChatUser user, String fallbackName) {
            return user != null
                    ? new ActorInfo(user.getFullName(), user.getAvatar())
                    : new ActorInfo(fallbackName, null);
        }
    }

    public ActorInfo fetchActorInfo(String userId) {
        return ActorInfo.of(chatUserRepository.findById(userId).orElse(null), "Người dùng");
    }

    public Conversation findGroupConversation(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));
        if (!conversation.isGroup()) throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        return conversation;
    }

    public ConversationResponse broadcastAndRespond(Conversation saved, String currentUserId) {
        broadcastConversationUpdate(saved);
        return buildConversationResponseForCurrentUser(saved, currentUserId);
    }

    public void assertMember(Conversation room, String userId) {
        boolean isMember = room.getMembers().stream()
                .anyMatch(m -> m.getProfileId().equals(userId) && isActiveMember(m));
        if (!isMember) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }

    public ConversationMember getMemberOrThrow(Conversation room, String userId) {
        return room.getMembers().stream()
                .filter(m -> m.getProfileId().equals(userId) && isActiveMember(m))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));
    }

    public boolean isActiveMember(ConversationMember member) {
        return !Boolean.FALSE.equals(member.getActive());
    }

    public MemberRole resolveRole(ConversationMember member) {
        return member.getRole() != null ? member.getRole() : MemberRole.MEMBER;
    }

    public void assertOwnerOrAdmin(ConversationMember actor) {
        MemberRole actorRole = resolveRole(actor);
        if (actorRole == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }

    public void assertSettingAllowed(Conversation conversation, String userId,
                                     Predicate<GroupSettings> settingCheck) {
        if (!conversation.isGroup()) return;
        GroupSettings settings = conversation.getSettings();
        if (settings == null || settingCheck.test(settings)) return;
        ConversationMember member = getMemberOrThrow(conversation, userId);
        if (resolveRole(member) == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }

    public void assertCanRemoveMember(ConversationMember actor, ConversationMember target) {
        MemberRole actorRole = resolveRole(actor);
        MemberRole targetRole = resolveRole(target);

        if (targetRole == MemberRole.OWNER) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
        if (actorRole == MemberRole.ADMIN && targetRole != MemberRole.MEMBER) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }

    public ConversationResponse buildConversationResponseForCurrentUser(Conversation room, String currentUserId) {
        Set<String> allUserIds = room.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getProfileId)
                .collect(Collectors.toSet());

        if (room.getLastMessage() != null && room.getLastMessage().getSenderId() != null) {
            allUserIds.add(room.getLastMessage().getSenderId());
        }

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = s3UtilV2.getS3BaseUrl();

        ChatUser partner = null;
        if (!room.isGroup()) {
            String partnerId = room.getMembers().stream()
                    .filter(this::isActiveMember)
                    .map(ConversationMember::getProfileId)
                    .filter(uid -> !uid.equals(currentUserId))
                    .findFirst()
                    .orElse(currentUserId);
            partner = resolvePartner(partnerId, currentUserId, userCache);
        }

        boolean viewerCanSee = canViewerSeeStatus(currentUserId, userCache);
        return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, null);
    }

    public ConversationResponse buildConversationResponse(
            Conversation room, ChatUser partner, String currentUserId,
            Map<String, ChatUser> userCache, String baseUrl, boolean viewerCanSee, String friendshipStatus) {
        return buildConversationResponse(room, partner, currentUserId, userCache, baseUrl, viewerCanSee, friendshipStatus, null);
    }

    public ConversationResponse buildConversationResponse(
            Conversation room, ChatUser partner, String currentUserId,
            Map<String, ChatUser> userCache, String baseUrl, boolean viewerCanSee,
            String friendshipStatus, Long pendingJoinRequestCount) {

        LastMessageInfo last = room.getLastMessage();
        if (last != null && last.getVisibleTo() != null && !last.getVisibleTo().isEmpty()
                && !last.getVisibleTo().contains(currentUserId)) {
            last = findFallbackLastMessage(room.getId(), currentUserId);
        }
        List<ConversationMemberResponse> members = buildMembersWithCache(
                room, currentUserId, userCache, baseUrl, viewerCanSee);

        String partnerDisplayName = safeDisplayName(partner != null ? partner.getFullName() : null);
        String displayName = room.getName();
        if (room.isGroup() && (displayName == null || displayName.isBlank())) {
            displayName = getDynamicGroupName(room, currentUserId, userCache);
        } else if (!room.isGroup()) {
            displayName = partnerDisplayName;
            if (displayName == null || displayName.isBlank()) {
                displayName = "Người dùng";
            }
        }
        String displayAvatar = room.isGroup()
                ? s3UtilV2.getFullUrl(room.getAvatar())
                : (partner != null ? s3UtilV2.getFullUrl(partner.getAvatar()) : null);

        ChatUser currentUser = userCache.get(currentUserId);
        Boolean isPinned = false;
        if (currentUser != null && currentUser.getPinnedConversations() != null) {
            isPinned = currentUser.getPinnedConversations().contains(room.getId());
        }

        return ConversationResponse.builder()
                .id(room.getId())
                .recipientId(room.isGroup() ? null : (partner != null ? partner.getId() : null))
                .name(displayName)
                .avatar(displayAvatar)
                .friendshipStatus(friendshipStatus)
                .isGroup(room.isGroup())
                .isDisbanded(room.isDisbanded())
                .unreadCount(room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(currentUserId, 0) : 0)
                .lastMessage(last != null ? LastMessageResponse.builder()
                        .id(last.getMessageId())
                        .senderId(last.getSenderId())
                        .senderName(resolveSenderName(last, userCache))
                        .content(last.getContent())
                        .timestamp(toOffset(last.getTimestamp()))
                        .type(last.getType())
                        .status(last.getStatus())
                        .isFromMe(last.getSenderId() != null && last.getSenderId().equals(currentUserId))
                        .metadata(last.getMetadata())
                        .build() : null)
                .members(members)
                .settings(room.isGroup() ? room.getSettings() : null)
                .joinLinkToken(room.isGroup() ? room.getJoinLinkToken() : null)
                .pendingJoinRequestCount(pendingJoinRequestCount != null ? pendingJoinRequestCount
                        : (room.isGroup() && room.getSettings() != null
                                && room.getSettings().isMembershipApprovalEnabled()
                                ? joinRequestRepository.countByConversationIdAndStatus(room.getId(), JoinRequestStatus.PENDING)
                                : null))
                .invitedUserIds(room.isGroup() && room.getInvitedUserIds() != null
                        ? new ArrayList<>(room.getInvitedUserIds())
                        : null)
                .isPinned(isPinned)
                .build();
    }

    public void broadcastConversationUpdate(String conversationId) {
        Conversation room = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));
        broadcastConversationUpdate(room);
    }

    public void broadcastConversationUpdate(Conversation room) {
        Set<String> userIds = room.getMembers().stream()
                .filter(this::isActiveMember)
                .map(ConversationMember::getProfileId)
                .collect(Collectors.toSet());

        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String baseUrl = s3UtilV2.getS3BaseUrl();

        Long pendingJoinRequestCount = room.isGroup() && room.getSettings() != null
                && room.getSettings().isMembershipApprovalEnabled()
                ? joinRequestRepository.countByConversationIdAndStatus(room.getId(), JoinRequestStatus.PENDING)
                : null;

        for (ConversationMember member : room.getMembers()) {
            if (!isActiveMember(member)) continue;
            String viewerId = member.getProfileId();
            boolean viewerCanSee = canViewerSeeStatus(viewerId, userCache);

            ChatUser partner = null;
            if (!room.isGroup()) {
                String partnerId = room.getMembers().stream()
                        .filter(this::isActiveMember)
                        .map(ConversationMember::getProfileId)
                        .filter(uid -> !uid.equals(viewerId))
                        .findFirst()
                        .orElse(viewerId);
                partner = resolvePartner(partnerId, viewerId, userCache);
            }

            ConversationResponse payload = buildConversationResponse(
                    room, partner, viewerId, userCache, baseUrl, viewerCanSee, null, pendingJoinRequestCount
            );

            // Use userId as SocketEvent targetUserId — the socket-service registers
            // WebSocket connections under userId (JWT sub), not profileId.
            String targetUserId = resolveUserId(viewerId, userCache);
            kafkaTemplate.send(kafkaTopicProperties.getSocketEvents().getSocketEvents(),
                    targetUserId,
                    new SocketEvent(SocketEventType.CONVERSATION, targetUserId, "/queue/conversations", payload));
        }
    }

    /**
     * Resolve the userId for a given profileId using the userCache.
     * Falls back to the profileId itself if the ChatUser record is absent or has no userId.
     * This is required because the socket-service routes WebSocket messages by userId (JWT sub).
     */
    public String resolveUserId(String profileId, Map<String, ChatUser> userCache) {
        ChatUser user = userCache.get(profileId);
        if (user != null && user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId();
        }
        return profileId; // fallback – should not happen in healthy data
    }


    public ChatUser resolvePartner(String partnerId, String currentUserId, Map<String, ChatUser> userCache) {
        if (partnerId.equals(currentUserId)) {
            return ChatUser.builder()
                    .id(partnerId)
                    .fullName("My Documents")
                    .avatar("cloud.png")
                    .build();
        }
        return userCache.getOrDefault(partnerId,
                ChatUser.builder().id(partnerId).fullName("Người dùng mới").build());
    }

    public boolean canViewerSeeStatus(String currentUserId, Map<String, ChatUser> userCache) {
        return true;
    }

    public String getDynamicGroupName(Conversation room, String currentUserId, Map<String, ChatUser> userCache) {
        List<String> memberNames = room.getMembers().stream()
                .filter(this::isActiveMember)
                .filter(m -> {
                    if (room.getMembers().size() <= 2) return true;
                    return !m.getProfileId().equals(currentUserId);
                })
                .map(m -> {
                    ChatUser u = userCache.get(m.getProfileId());
                    return u != null ? u.getFullName() : "Người dùng";
                })
                .filter(name -> name != null && !name.isBlank())
                .limit(4) 
                .toList();

        if (memberNames.isEmpty()) return "Nhóm";

        String joined = String.join(", ", memberNames);
        int totalMembers = (int) room.getMembers().stream().filter(this::isActiveMember).count();
        int remaining = totalMembers - memberNames.size();

        if (remaining > 0) {
            return joined + " và " + remaining + " người khác";
        }
        return joined;
    }

    public String safeDisplayName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Người dùng";
        return fullName;
    }

    private List<ConversationMemberResponse> buildMembersWithCache(
            Conversation room, String currentUserId, Map<String, ChatUser> userCache,
            String baseUrl, boolean viewerCanSee) {

        return room.getMembers().stream()
                .filter(this::isActiveMember)
                .sorted(Comparator
                        .comparing((ConversationMember m) ->
                                        m.getJoinedAt() != null ? m.getJoinedAt() : LocalDateTime.MIN,
                                Comparator.reverseOrder())
                        .thenComparingInt(m -> {
                            MemberRole r = m.getRole() != null ? m.getRole() : MemberRole.MEMBER;
                            return r == MemberRole.OWNER ? 0 : (r == MemberRole.ADMIN ? 1 : 2);
                        }))
                .map(m -> {
                    ChatUser memberInfo = userCache.get(m.getProfileId());

                    return ConversationMemberResponse.builder()
                            .userId(m.getProfileId())
                            .profileId(m.getProfileId())   // userId now stores profileId
                            .fullName(memberInfo != null ? memberInfo.getFullName() : "Người dùng")
                            .avatar(memberInfo != null ? s3UtilV2.getFullUrl(memberInfo.getAvatar()) : null)
                            .lastReadMessageId(m.getLastReadMessageId())
                            .role(m.getRole() != null ? m.getRole() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public OffsetDateTime toOffset(LocalDateTime time) {
        if (time == null) return null;
        return time.atOffset(ZoneOffset.ofHours(7));
    }

    public OffsetDateTime toOffsetFromMongo(Object time) {
        if (time == null) return null;
        if (time instanceof LocalDateTime localDateTime) return toOffset(localDateTime);
        if (time instanceof Date date) return date.toInstant().atOffset(ZoneOffset.ofHours(7));
        return null;
    }

    public boolean isPhoneNumber(String query) {
        return PhoneUtil.isValidVnPhone(query);
    }

    private LastMessageInfo findFallbackLastMessage(String conversationId, String currentUserId) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("conversationId").is(conversationId),
                Criteria.where("deletedBy").ne(currentUserId),
                new Criteria().orOperator(
                        Criteria.where("visibleTo").exists(false),
                        Criteria.where("visibleTo").is(null),
                        Criteria.where("visibleTo").size(0),
                        Criteria.where("visibleTo").is(currentUserId)
                )
        );
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(1);
        Message msg = mongoTemplate.findOne(query, Message.class);
        if (msg == null) return null;
        return LastMessageInfo.builder()
                .messageId(msg.getId())
                .senderId(msg.getSenderId())
                .content(msg.getContent())
                .timestamp(msg.getCreatedAt())
                .type(msg.getType())
                .status(msg.getStatus())
                .metadata(msg.getMetadata())
                .build();
    }

    private String resolveSenderName(LastMessageInfo last, Map<String, ChatUser> userCache) {
        if (last.getSenderId() == null) return null;
        ChatUser cached = userCache.get(last.getSenderId());
        if (cached != null && cached.getFullName() != null && !cached.getFullName().isBlank()) {
            return cached.getFullName();
        }
        if (last.getMetadata() != null) {
            Object actorName = last.getMetadata().get("actorName");
            if (actorName instanceof String name && !name.isBlank()) {
                return name;
            }
        }
        return "";
    }
}
