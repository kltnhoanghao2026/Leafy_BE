package com.leafy.messageservice.service.message;

import com.leafy.messageservice.model.PinnedMessageInfo;

import java.util.List;

public interface PinService {
    List<PinnedMessageInfo> getPins(String conversationId);
    PinnedMessageInfo pinMessage(String conversationId, String messageId);
    void unpinMessage(String conversationId, String messageId);
}
