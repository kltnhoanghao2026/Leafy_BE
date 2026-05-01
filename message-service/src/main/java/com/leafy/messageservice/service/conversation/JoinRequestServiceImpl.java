package com.leafy.messageservice.service.conversation;

import com.leafy.messageservice.dto.response.PageResponse;
import com.leafy.messageservice.model.enums.SystemActionType;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import com.leafy.common.utils.S3UtilV2;
import com.leafy.messageservice.dto.request.JoinByLinkRequest;
import com.leafy.messageservice.dto.response.ConversationResponse;
import com.leafy.messageservice.dto.response.JoinGroupPreviewResponse;
import com.leafy.messageservice.dto.response.JoinRequestResponse;
import com.leafy.messageservice.model.ChatUser;
import com.leafy.messageservice.model.Conversation;
import com.leafy.messageservice.model.ConversationMember;
import com.leafy.messageservice.model.GroupSettings;
import com.leafy.messageservice.model.JoinRequest;
import com.leafy.messageservice.model.enums.JoinMethod;
import com.leafy.messageservice.model.enums.JoinRequestStatus;
import com.leafy.messageservice.model.enums.MemberRole;
import com.leafy.messageservice.repository.ChatUserRepository;
import com.leafy.messageservice.repository.ConversationRepository;
import com.leafy.messageservice.repository.JoinRequestRepository;
import com.leafy.messageservice.service.message.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinRequestServiceImpl implements JoinRequestService {

    private final ConversationRepository conversationRepository;
    private final ChatUserRepository chatUserRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final SystemMessageService systemMessageService;
    private final ConversationHelper helper;
    private final S3UtilV2 s3UtilV2;

    @Override
    public ConversationResponse joinByLink(String token, JoinByLinkRequest request) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();

        Conversation conversation = conversationRepository.findByJoinLinkToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));

        if (!conversation.isGroup()) throw new AppException(ErrorCode.SYS_UNCATEGORIZED);

        GroupSettings settings = conversation.getSettings();
        if (settings == null || !settings.isJoinByLinkEnabled()) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        boolean isAlreadyActive = conversation.getMembers().stream()
                .anyMatch(m -> m.getProfileId().equals(currentUserId) && helper.isActiveMember(m));
        if (isAlreadyActive) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        // Check if user is blocked from this group
        if (conversation.getBlockedUserIds() != null && conversation.getBlockedUserIds().contains(currentUserId)) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        // Invited users (from group creation) bypass approval on first join
        boolean isInvited = conversation.getInvitedUserIds() != null
                && conversation.getInvitedUserIds().contains(currentUserId);

        if (!isInvited && (settings.isMembershipApprovalEnabled() || settings.getJoinQuestion() != null)) {
            String joinAnswer = request != null ? request.joinAnswer() : null;
            return handleJoinRequest(conversation, currentUserId, joinAnswer);
        }

        // Remove from invited list so subsequent rejoins require approval
        if (isInvited && conversation.getInvitedUserIds() != null) {
            conversation.getInvitedUserIds().remove(currentUserId);
        }

        return directJoinByLink(conversation, currentUserId);
    }

    @Override
    public JoinGroupPreviewResponse getJoinPreview(String token) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();

        Conversation conversation = conversationRepository.findByJoinLinkToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));

        if (!conversation.isGroup()) throw new AppException(ErrorCode.SYS_UNCATEGORIZED);

        GroupSettings settings = conversation.getSettings();
        if (settings == null || !settings.isJoinByLinkEnabled()) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        Set<ConversationMember> activeMembers = conversation.getMembers().stream()
                .filter(helper::isActiveMember).collect(Collectors.toSet());

        boolean isAlreadyMember = activeMembers.stream()
                .anyMatch(m -> m.getProfileId().equals(currentUserId));

        Set<String> allMemberIds = activeMembers.stream()
                .map(ConversationMember::getProfileId).collect(Collectors.toSet());
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(allMemberIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        String ownerUserId = activeMembers.stream()
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .map(ConversationMember::getProfileId)
                .findFirst().orElse(null);
        String createdByName = ownerUserId != null && userCache.containsKey(ownerUserId)
                ? userCache.get(ownerUserId).getFullName() : null;

        String baseUrl = s3UtilV2.getS3BaseUrl();
        List<JoinGroupPreviewResponse.MemberPreview> memberPreviews = activeMembers.stream()
                .map(ConversationMember::getProfileId)
                .limit(5)
                .map(userCache::get)
                .filter(Objects::nonNull)
                .map(u -> JoinGroupPreviewResponse.MemberPreview.builder()
                        .name(u.getFullName())
                        .avatar(u.getAvatar() != null ? baseUrl + u.getAvatar() : null)
                        .build())
                .collect(Collectors.toList());

        String groupName = conversation.getName();
        if (groupName == null || groupName.isBlank()) {
            groupName = helper.getDynamicGroupName(conversation, currentUserId, userCache);
        }

        boolean hasPendingRequest = joinRequestRepository.existsByConversationIdAndUserIdAndStatus(
                conversation.getId(), currentUserId, JoinRequestStatus.PENDING);

        return JoinGroupPreviewResponse.builder()
                .conversationId(conversation.getId())
                .groupName(groupName)
                .groupAvatar(conversation.getAvatar() != null ? baseUrl + conversation.getAvatar() : null)
                .memberCount(activeMembers.size())
                .createdByName(createdByName)
                .memberPreviews(memberPreviews)
                .isAlreadyMember(isAlreadyMember)
                .isBlockedFromGroup(conversation.getBlockedUserIds() != null && conversation.getBlockedUserIds().contains(currentUserId))
                .membershipApprovalEnabled(settings.isMembershipApprovalEnabled())
                .hasPendingRequest(hasPendingRequest)
                .joinQuestion(settings.getJoinQuestion())
                .build();
    }

    @Override
    public PageResponse<List<JoinRequestResponse>> getJoinRequests(String conversationId, int page, int size) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        Page<JoinRequest> requestPage = joinRequestRepository.findByConversationIdAndStatus(
                conversationId, JoinRequestStatus.PENDING,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));

        Set<String> userIds = requestPage.getContent().stream()
                .map(JoinRequest::getUserId).collect(Collectors.toSet());
        String baseUrl = s3UtilV2.getS3BaseUrl();
        Map<String, ChatUser> userCache = chatUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(ChatUser::getId, u -> u));

        List<JoinRequestResponse> responses = requestPage.getContent().stream()
                .map(req -> {
                    ChatUser user = userCache.get(req.getUserId());
                    return JoinRequestResponse.builder()
                            .id(req.getId())
                            .conversationId(req.getConversationId())
                            .userId(req.getUserId())
                            .profileId(req.getUserId())
                            .fullName(user != null ? user.getFullName() : "Người dùng")
                            .avatar(user != null && user.getAvatar() != null ? baseUrl + user.getAvatar() : null)
                            .status(req.getStatus())
                            .requestedAt(req.getCreatedAt())
                            .processedAt(req.getProcessedAt())
                            .processedBy(req.getProcessedBy())
                            .joinAnswer(req.getJoinAnswer())
                            .build();
                })
                .collect(Collectors.toList());

        return PageResponse.<List<JoinRequestResponse>>builder()
                .page(page)
                .limit(size)
                .totalItems(requestPage.getTotalElements())
                .totalPages(requestPage.getTotalPages())
                .data(responses)
                .build();
    }

    @Override
    public ConversationResponse approveJoinRequest(String conversationId, String requestId) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));

        if (!joinRequest.getConversationId().equals(conversationId)) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        joinRequest.setStatus(JoinRequestStatus.APPROVED);
        joinRequest.setProcessedAt(LocalDateTime.now());
        joinRequest.setProcessedBy(currentUserId);
        joinRequestRepository.save(joinRequest);

        String targetUserId = joinRequest.getUserId();
        ConversationResponse response = addMemberToConversation(conversation, targetUserId);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        var targetInfo = helper.fetchActorInfo(targetUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId,
                actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_REQUEST_APPROVED,
                Map.of("targetIds", List.of(targetUserId),
                        "payload", Map.of("targetNames", List.of(targetInfo.name()),
                                "targetAvatars", List.of(targetInfo.avatar() != null ? targetInfo.avatar() : ""))));

        log.info("[Group] Join request {} approved by {} for user {} in conversation {}",
                requestId, currentUserId, targetUserId, conversationId);
        return response;
    }

    @Override
    public void rejectJoinRequest(String conversationId, String requestId) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        JoinRequest joinRequest = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));

        if (!joinRequest.getConversationId().equals(conversationId)) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
        if (joinRequest.getStatus() != JoinRequestStatus.PENDING) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        joinRequest.setStatus(JoinRequestStatus.REJECTED);
        joinRequest.setProcessedAt(LocalDateTime.now());
        joinRequest.setProcessedBy(currentUserId);
        joinRequestRepository.save(joinRequest);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversationId, currentUserId,
                actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_REQUEST_REJECTED, Map.of(),
                Set.of(joinRequest.getUserId()));

        log.info("[Group] Join request {} rejected by {} for user {} in conversation {}",
                requestId, currentUserId, joinRequest.getUserId(), conversationId);
    }

    @Override
    public void cancelMyJoinRequest(String conversationId) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();

        JoinRequest joinRequest = joinRequestRepository
                .findByConversationIdAndUserIdAndStatus(conversationId, currentUserId, JoinRequestStatus.PENDING)
                .orElseThrow(() -> new AppException(ErrorCode.SYS_UNCATEGORIZED));

        joinRequest.setStatus(JoinRequestStatus.CANCELLED);
        joinRequest.setProcessedAt(LocalDateTime.now());
        joinRequest.setProcessedBy(currentUserId);
        joinRequestRepository.save(joinRequest);

        log.info("[Group] User {} cancelled join request for conversation {}", currentUserId, conversationId);
    }

    @Override
    public void updateJoinQuestion(String conversationId, String question) {
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();
        Conversation conversation = helper.findGroupConversation(conversationId);
        ConversationMember actor = helper.getMemberOrThrow(conversation, currentUserId);
        helper.assertOwnerOrAdmin(actor);

        GroupSettings settings = conversation.getSettings();
        if (settings == null) {
            settings = GroupSettings.builder().build();
            conversation.setSettings(settings);
        }
        if (!settings.isMembershipApprovalEnabled()) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        settings.setJoinQuestion(question != null ? question.trim() : null);
        conversationRepository.save(conversation);

        log.info("[Group] Join question updated for conversation {} by user {}", conversationId, currentUserId);
    }

    // ─────────────────────────── Private helpers ───────────────────────────

    private ConversationResponse handleJoinRequest(Conversation conversation, String currentUserId, String joinAnswer) {
        boolean alreadyPending = joinRequestRepository.existsByConversationIdAndUserIdAndStatus(
                conversation.getId(), currentUserId, JoinRequestStatus.PENDING);
        if (alreadyPending) {
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }

        GroupSettings settings = conversation.getSettings();
        if (settings != null && settings.getJoinQuestion() != null) {
            if (joinAnswer == null || joinAnswer.isBlank()) {
                throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
            }
        }

        JoinRequest joinRequest = JoinRequest.builder()
                .conversationId(conversation.getId())
                .userId(currentUserId)
                .status(JoinRequestStatus.PENDING)
                .joinAnswer(joinAnswer)
                .build();
        joinRequestRepository.save(joinRequest);

        Set<String> adminIds = conversation.getMembers().stream()
                .filter(m -> helper.isActiveMember(m) && helper.resolveRole(m) != MemberRole.MEMBER)
                .map(ConversationMember::getProfileId)
                .collect(Collectors.toSet());

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(conversation.getId(), currentUserId,
                actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_REQUEST_CREATED, Map.of(),
                adminIds);

        log.info("[Group] Join request created by user {} for conversation {}", currentUserId, conversation.getId());

        return null;
    }

    private ConversationResponse directJoinByLink(Conversation conversation, String currentUserId) {
        LocalDateTime now = LocalDateTime.now();

        // Clear self-block on voluntary rejoin
        if (conversation.getSelfBlockedUserIds() != null) {
            conversation.getSelfBlockedUserIds().remove(currentUserId);
        }

        ConversationMember existingMember = conversation.getMembers().stream()
                .filter(m -> m.getProfileId().equals(currentUserId))
                .findFirst().orElse(null);

        if (existingMember != null) {
            existingMember.setActive(true);
            existingMember.setRemovedAt(null);
            existingMember.setRemovedBy(null);
            existingMember.setRole(MemberRole.MEMBER);
            existingMember.setJoinedAt(now);
            existingMember.setJoinMethod(JoinMethod.JOIN_BY_LINK);
            existingMember.setAddedBy(null);
        } else {
            conversation.getMembers().add(
                    ConversationMember.builder().profileId(currentUserId).role(MemberRole.MEMBER).joinedAt(now)
                    .joinMethod(JoinMethod.JOIN_BY_LINK).build());
        }

        if (conversation.getUnreadCounts() == null) conversation.setUnreadCounts(new HashMap<>());
        conversation.getUnreadCounts().putIfAbsent(currentUserId, 0);

        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        conversation.getDeletedBefore().remove(currentUserId);

        Conversation saved = conversationRepository.save(conversation);

        var actorInfo = helper.fetchActorInfo(currentUserId);
        systemMessageService.sendSystemMessage(saved.getId(), currentUserId, actorInfo.name(), actorInfo.avatar(),
                SystemActionType.JOIN_BY_LINK, Map.of());

        log.info("[Group] User {} joined conversation {} via link", currentUserId, saved.getId());
        return helper.broadcastAndRespond(saved, currentUserId);
    }

    private ConversationResponse addMemberToConversation(Conversation conversation, String userId) {
        LocalDateTime now = LocalDateTime.now();
        String currentUserId = helper.getSecurityUtil().getCurrentProfileId();

        // Clear self-block if approved via join request
        if (conversation.getSelfBlockedUserIds() != null) {
            conversation.getSelfBlockedUserIds().remove(userId);
        }

        ConversationMember existingMember = conversation.getMembers().stream()
                .filter(m -> m.getProfileId().equals(userId))
                .findFirst().orElse(null);

        if (existingMember != null) {
            existingMember.setActive(true);
            existingMember.setRemovedAt(null);
            existingMember.setRemovedBy(null);
            existingMember.setRole(MemberRole.MEMBER);
            existingMember.setJoinedAt(now);
            existingMember.setJoinMethod(JoinMethod.ADDED_BY_MEMBER);
            existingMember.setAddedBy(currentUserId);
        } else {
            conversation.getMembers().add(
                    ConversationMember.builder().profileId(userId).role(MemberRole.MEMBER).joinedAt(now)
                    .joinMethod(JoinMethod.ADDED_BY_MEMBER).addedBy(currentUserId).build());
        }

        if (conversation.getUnreadCounts() == null) conversation.setUnreadCounts(new HashMap<>());
        conversation.getUnreadCounts().putIfAbsent(userId, 0);

        if (conversation.getDeletedBefore() == null) conversation.setDeletedBefore(new HashMap<>());
        conversation.getDeletedBefore().remove(userId);

        Conversation saved = conversationRepository.save(conversation);
        return helper.broadcastAndRespond(saved, userId);
    }
}
