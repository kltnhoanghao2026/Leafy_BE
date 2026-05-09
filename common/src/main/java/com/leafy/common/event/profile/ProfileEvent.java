package com.leafy.common.event.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileEvent {
    private String profileId;
    private String userId;       // Auth service UUID — used as ChatUser._id in message-service
    private String fullName;
    private String avatar;
    private String role;
    private Boolean isVerified;
    private Long timestamp;
}
