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
    private String userId;       // Auth service UUID (JWT sub) — used for WebSocket routing in message/notification services
    private String fullName;
    private String avatar;
    private String role;
    private Boolean isVerified;
    private Long timestamp;
}
