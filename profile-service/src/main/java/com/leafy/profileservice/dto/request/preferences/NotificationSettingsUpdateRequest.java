package com.leafy.profileservice.dto.request.preferences;

/**
 * Request DTO for updating notification settings
 */
public record NotificationSettingsUpdateRequest(
        Boolean notifyNewMessageFromDirect,
        Boolean previewNewMessageFromDirect,
        Boolean notifyNewMessageFromGroup,
        Boolean notifyNewPostFromFriend,
        Boolean notifyNewMessage
) {
}
