package com.leafy.messageservice.mapper;

import com.leafy.messageservice.dto.response.AttachmentInfoResponse;
import com.leafy.messageservice.dto.response.ChatNotification;
import com.leafy.messageservice.dto.response.LinkPreviewResponse;
import com.leafy.messageservice.dto.response.MessageResponse;
import com.leafy.messageservice.dto.response.ReplyMetadataResponse;
import com.leafy.messageservice.model.AttachmentInfo;
import com.leafy.messageservice.model.LinkPreview;
import com.leafy.messageservice.model.Message;
import com.leafy.messageservice.model.LastMessageInfo;
import com.leafy.messageservice.dto.response.ReplyMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MessageMapper {
    
    default OffsetDateTime map(LocalDateTime value) {
        if (value == null) return null;
        return value.atOffset(ZoneOffset.ofHours(7));
    }

    /**
     * Map a single AttachmentInfo to its response DTO, constructing the public URL
     * from baseUrl + key when the stored url field is absent (legacy records).
     */
    default AttachmentInfoResponse mapAttachment(AttachmentInfo info, String baseUrl) {
        if (info == null) return null;
        String url = (info.getUrl() != null && !info.getUrl().isBlank())
                ? info.getUrl()
                : (baseUrl != null && info.getKey() != null ? baseUrl + info.getKey() : null);
        return AttachmentInfoResponse.builder()
                .key(info.getKey())
                .url(url)
                .fileName(info.getFileName())
                .originalFileName(info.getOriginalFileName())
                .contentType(info.getContentType())
                .size(info.getSize())
                .build();
    }

    /** Map a list of AttachmentInfo, constructing URLs from key when url is absent. */
    default List<AttachmentInfoResponse> mapAttachments(List<AttachmentInfo> attachments, String baseUrl) {
        if (attachments == null || attachments.isEmpty()) return Collections.emptyList();
        return attachments.stream()
                .map(a -> mapAttachment(a, baseUrl))
                .collect(Collectors.toList());
    }

    @Mapping(target = "senderAvatar", expression = "java(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)")
    @Mapping(target = "replyTo", source = "msg.replyTo")
    @Mapping(target = "metadata", source = "msg.metadata")
    @Mapping(target = "attachments", expression = "java(mapAttachments(msg.getAttachments(), baseUrl))")
    @Mapping(target = "linkPreview", source = "msg.linkPreview")
    @Mapping(target = "reactions", source = "msg.reactions")
    MessageResponse mapToMessageResponse(Message msg, String baseUrl);

    @Mapping(target = "senderAvatar", expression = "java(msg.getSenderAvatar() != null ? baseUrl + msg.getSenderAvatar() : null)")
    @Mapping(target = "timestamp", source = "msg.createdAt")
    @Mapping(target = "replyTo", source = "msg.replyTo")
    @Mapping(target = "unreadCount", source = "unreadCount")
    @Mapping(target = "metadata", source = "msg.metadata")
    @Mapping(target = "attachments", expression = "java(mapAttachments(msg.getAttachments(), baseUrl))")
    @Mapping(target = "linkPreview", source = "msg.linkPreview")
    @Mapping(target = "reactions", source = "msg.reactions")
    ChatNotification mapToChatNotification(Message msg, String baseUrl, Integer unreadCount);

    ReplyMetadataResponse mapToReplyMetadataResponse(ReplyMetadata metadata);

    /** Kept for any direct callers — uses key as url fallback with no baseUrl. */
    default AttachmentInfoResponse mapToAttachmentInfoResponse(AttachmentInfo info) {
        return mapAttachment(info, null);
    }

    LinkPreviewResponse mapToLinkPreviewResponse(LinkPreview linkPreview);

    LinkPreviewResponse.MemberSnapshot mapToMemberSnapshot(LinkPreview.MemberSnapshot snapshot);

    @Mapping(target = "messageId", source = "msg.id")
    @Mapping(target = "timestamp", source = "msg.createdAt")
    LastMessageInfo mapToLastMessageInfo(Message msg);
}
