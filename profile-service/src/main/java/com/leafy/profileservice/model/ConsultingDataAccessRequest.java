package com.leafy.profileservice.model;

import com.leafy.profileservice.model.enums.AccessRequestStatus;
import com.leafy.profileservice.model.enums.ConsultingDataType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Tracks expert requests to access specific data types of a consulted farmer.
 * A request is per (expert, farmer, dataType) tuple.
 */
@Document(collection = "consulting_data_access_requests")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@CompoundIndexes({
    @CompoundIndex(name = "expert_farmer_type_idx",
                   def = "{'expertProfileId': 1, 'farmerProfileId': 1, 'dataType': 1}",
                   unique = true),
    @CompoundIndex(name = "farmer_status_idx",
                   def = "{'farmerProfileId': 1, 'status': 1}")
})
public class ConsultingDataAccessRequest {

    @Id
    private String id;

    private String expertProfileId;

    private String farmerProfileId;

    private ConsultingDataType dataType;

    @Builder.Default
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    private String expertMessage;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
