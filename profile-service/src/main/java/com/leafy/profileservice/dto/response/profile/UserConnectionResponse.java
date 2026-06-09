package com.leafy.profileservice.dto.response.profile;

import com.leafy.profileservice.model.enums.ConsultationStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserConnectionResponse {
    String id;
    String followerId;
    String followingId;
    Boolean isFollowing;
    ConsultationStatus consultationStatus;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
