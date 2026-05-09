package com.leafy.messageservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Minimal profile payload received from profile-service during batch sync.
 * Maps to the fields returned by GET /internal/profiles/batch (UserSyncResponse).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileSyncEntry {

    /** profileId — becomes ChatUser._id */
    String id;

    /** auth account UUID */
    String userId;

    String fullName;

    String avatar;

    boolean active;
}
