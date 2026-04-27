package com.leafy.messageservice.service.message;

import com.leafy.messageservice.model.enums.MessageType;
import com.leafy.messageservice.model.enums.SystemActionType;
import com.leafy.common.utils.S3UtilV2;
import com.leafy.common.utils.S3UtilV2;
import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.dto.client.socketservice.SocketEvent;
import com.leafy.common.enums.SocketEventType;
import com.leafy.messageservice.dto.response.ChatNotification;
import com.leafy.messageservice.mapper.MessageMapper;
import com.leafy.messageservice.model.Conversation;
import com.leafy.messageservice.model.LastMessageInfo;
import com.leafy.messageservice.model.Message;
import com.leafy.messageservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMessageServiceImpl implements SystemMessageService {

    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final S3UtilV2 s3UtilV2;
    private final KafkaTopicProperties kafkaTopicProperties;

    @Override
    public void sendSystemMessage(String conversationId, String actorId, String actorName, String actorAvatar,
                                  SystemActionType action, Map<String, Object> extraMetadata) {
        sendSystemMessage(conversationId, actorId, actorName, actorAvatar, action, extraMetadata, null);
    }

    @Override
    public void sendSystemMessage(String conversationId, String actorId, String actorName, String actorAvatar,
                                  SystemActionType action, Map<String, Object> extraMetadata,
                                  Set<String> recipientUserIds) {
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("action", action.name());
        metadata.put("actorName", actorName);
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(actorId)
                .senderName(actorName)
                .senderAvatar(actorAvatar)
                .type(MessageType.SYSTEM)
                .metadata(metadata)
                .visibleTo(recipientUserIds != null && !recipientUserIds.isEmpty()
                        ? new HashSet<>(recipientUserIds) : null)
                .build();
        message.setCreatedAt(now);
        Message savedMessage = messageRepository.save(message);

        boolean isRestricted = recipientUserIds != null && !recipientUserIds.isEmpty();

        boolean isNegativeAction = action == SystemActionType.LEAVE_GROUP
                || action == SystemActionType.DISBAND_GROUP;

        Conversation room;
        Query query = new Query(Criteria.where("id").is(conversationId));

        if (isRestricted) {
            Conversation existing = mongoTemplate.findOne(query, Conversation.class);
            LocalDateTime preservedTimestamp = (existing != null && existing.getLastMessage() != null
                    && existing.getLastMessage().getTimestamp() != null)
                    ? existing.getLastMessage().getTimestamp()
                    : now;

            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(actorId)
                    .content(null)
                    .timestamp(preservedTimestamp)
                    .type(MessageType.SYSTEM)
                    .metadata(savedMessage.getMetadata())
                    .visibleTo(new HashSet<>(recipientUserIds))
                    .build());

            room = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Conversation.class);
        } else if (isNegativeAction) {
            Conversation existing = mongoTemplate.findOne(query, Conversation.class);
            LocalDateTime preservedTimestamp = (existing != null && existing.getLastMessage() != null
                    && existing.getLastMessage().getTimestamp() != null)
                    ? existing.getLastMessage().getTimestamp()
                    : now;

            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(actorId)
                    .content(null)
                    .timestamp(preservedTimestamp)
                    .type(MessageType.SYSTEM)
                    .metadata(savedMessage.getMetadata())
                    .build());

            room = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Conversation.class);
        } else {
            Update update = new Update().set("lastMessage", LastMessageInfo.builder()
                    .messageId(savedMessage.getId())
                    .senderId(actorId)
                    .content(null)
                    .timestamp(savedMessage.getCreatedAt())
                    .type(MessageType.SYSTEM)
                    .metadata(savedMessage.getMetadata())
                    .build());

            room = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true),
                    Conversation.class);
        }

        if (room != null) {
            String baseUrl = s3UtilV2.getS3BaseUrl();

            room.getMembers().forEach(member -> {
                if (recipientUserIds != null && !recipientUserIds.contains(member.getUserId())) {
                    return;
                }

                Integer currentUnread = room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(member.getUserId(), 0) : 0;
                boolean isFromMe = member.getUserId().equals(actorId);

                ChatNotification notification = messageMapper.mapToChatNotification(savedMessage, baseUrl, currentUnread);
                notification = notification.toBuilder().isFromMe(isFromMe).build();

                kafkaTemplate.send(kafkaTopicProperties.getSocketEvents().getSocketEvents(),
                        new SocketEvent(SocketEventType.MESSAGE, member.getUserId(),
                                "/queue/messages", notification));
            });
        }
    }
}
