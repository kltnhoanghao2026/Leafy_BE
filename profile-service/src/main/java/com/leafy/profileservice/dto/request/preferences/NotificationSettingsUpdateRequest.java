package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating notification settings
 */
public record NotificationSettingsUpdateRequest(
        Boolean notifyNewMessageFromDirect,
        Boolean previewNewMessageFromDirect,
        Boolean notifyNewMessageFromGroup,
        Boolean notifyCall,
        Boolean notifyNewPostFromFriend,
        Boolean notifyDOB,
        Boolean notifyNewMessage,
        Boolean shakeOnNewMessage,
        Boolean previewNewMessage
) {
}
