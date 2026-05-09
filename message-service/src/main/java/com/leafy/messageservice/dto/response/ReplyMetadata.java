package com.leafy.messageservice.dto.response;

import com.leafy.messageservice.model.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyMetadata {
    String messageId;
    String senderId;
    String content;
    MessageType type; 
}
