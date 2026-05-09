package com.leafy.notificationservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Minimal profile payload received from profile-service during batch sync.
 * Maps to the fields returned by GET /internal/profiles/batch (UserSyncResponse).
 *
 * <p>Mirrors {@code message-service}'s {@code ProfileSyncEntry}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileSyncEntry {

    /** profileId — becomes NotificationUser._id */
    String id;

    /** Auth account UUID — stored as NotificationUser.accountId */
    String userId;

    String fullName;

    String avatar;

    boolean active;
}
