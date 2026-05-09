package com.leafy.messageservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.messageservice.model.enums.MessageStatus;
import com.leafy.messageservice.model.enums.MessageType;
import com.leafy.messageservice.dto.response.ReplyMetadata;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
@CompoundIndex(name = "conversationId_createdAt_idx", def = "{'conversationId': 1, 'createdAt': -1}")
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class Message extends BaseModel {
    @Id
    String id;
    String conversationId; // ObjectId của Conversation._id
    String senderId;
    String senderName;
    String senderAvatar;
    String content;
    String clientMessageId;
    MessageType type;
    ReplyMetadata replyTo;
    @Builder.Default
    boolean isForwarded = false;

    @Builder.Default
    boolean isEdited = false;

    @Builder.Default
    MessageStatus status = MessageStatus.NORMAL;

    Map<String, Object> metadata; // system message action data
    String deletedByAdminId;       // userId of admin/owner who deleted this message
    List<AttachmentInfo> attachments;

    LinkPreview linkPreview;

    Map<String, List<String>> reactions; // emoji → list of userIds (allows multiple reactions per user)

    @Builder.Default
    Set<String> deletedBy = new HashSet<>();

    Set<String> visibleTo;
}
