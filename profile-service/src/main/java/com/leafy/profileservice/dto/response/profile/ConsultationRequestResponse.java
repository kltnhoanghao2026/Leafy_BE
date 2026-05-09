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
public class ConsultationRequestResponse {
    String connectionId;
    String followerId;
    String followerName;
    String followerAvatar;
    String followerRole;
    LocalDateTime requestedAt;
    ConsultationStatus status;
}
