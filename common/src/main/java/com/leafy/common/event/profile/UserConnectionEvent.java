package com.leafy.common.event.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConnectionEvent {
    private String followerId;
    private String followingId;
    private String action; // e.g., "FOLLOW", "UNFOLLOW"
    private Long timestamp;
}
