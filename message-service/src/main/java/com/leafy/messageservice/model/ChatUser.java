package com.leafy.messageservice.model;

import com.leafy.common.enums.Status;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_users")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatUser {
    @Id
    String id;
    String accountId;
    String fullName;
    String email;
    Status status;
    String avatar;
    LocalDateTime lastUpdatedAt;

    @Indexed(unique = true, sparse = true)
    String phoneNumber;

    @Builder.Default
    boolean isInvisible = false;

    @Builder.Default
    List<String> pinnedConversations = new ArrayList<>();
}
