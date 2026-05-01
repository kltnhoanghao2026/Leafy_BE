package com.leafy.messageservice.service.message;

import com.leafy.messageservice.model.enums.MessageType;
import com.leafy.messageservice.model.enums.SystemActionType;
import com.leafy.common.utils.S3UtilV2;
import com.leafy.common.config.kafka.KafkaTopicProperties;
import com.leafy.common.dto.client.socketservice.SocketEvent;
import com.leafy.common.enums.SocketEventType;
import com.leafy.messageservice.dto.response.ChatNotification;
import com.leafy.messageservice.mapper.MessageMapper;
import com.leafy.messageservice.model.ChatUser;
import com.leafy.messageservice.model.Conversation;
import com.leafy.messageservice.model.LastMessageInfo;
import com.leafy.messageservice.model.Message;
import com.leafy.messageservice.repository.ChatUserRepository;
import com.leafy.messageservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.leafy.messageservice.service.conversation.ConversationHelper;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemMessageServiceImpl implements SystemMessageService {

    private final MessageRepository messageRepository;
    private final ChatUserRepository chatUserRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageMapper messageMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final S3UtilV2 s3UtilV2;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final ConversationHelper conversationHelper;

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

            // Batch-resolve accountIds for all relevant members (socket-service routes by accountId)
            Set<String> sysProfileIds = room.getMembers().stream()
                    .filter(m -> recipientUserIds == null || recipientUserIds.contains(m.getProfileId()))
                    .map(com.leafy.messageservice.model.ConversationMember::getProfileId)
                    .collect(Collectors.toSet());
            Map<String, ChatUser> sysCache = chatUserRepository.findAllById(sysProfileIds).stream()
                    .collect(Collectors.toMap(ChatUser::getId, u -> u));

            room.getMembers().forEach(member -> {
                if (recipientUserIds != null && !recipientUserIds.contains(member.getProfileId())) {
                    return;
                }

                Integer currentUnread = room.getUnreadCounts() != null
                        ? room.getUnreadCounts().getOrDefault(member.getProfileId(), 0) : 0;
                boolean isFromMe = member.getProfileId().equals(actorId);

                ChatNotification notification = messageMapper.mapToChatNotification(savedMessage, baseUrl, currentUnread);
                notification = notification.toBuilder().isFromMe(isFromMe).build();

                String targetAccountId = conversationHelper.resolveAccountId(member.getProfileId(), sysCache);
                kafkaTemplate.send(kafkaTopicProperties.getSocketEvents().getSocketEvents(),
                        new SocketEvent(SocketEventType.MESSAGE, targetAccountId,
                                "/queue/messages", notification));
            });
        }
    }
}
