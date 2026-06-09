package com.leafy.profileservice.dto.response.access;

import com.leafy.profileservice.model.enums.AccessRequestStatus;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultingDataAccessRequestResponse {
    private String id;
    private String expertProfileId;
    private String expertName;
    private String expertAvatar;
    private String farmerProfileId;
    private ConsultingDataType dataType;
    private AccessRequestStatus status;
    private String expertMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}
