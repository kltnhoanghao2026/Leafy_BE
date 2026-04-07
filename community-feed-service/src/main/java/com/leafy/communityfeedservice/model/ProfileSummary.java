package com.leafy.communityfeedservice.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("profile_summaries")
public class ProfileSummary {

    @MongoId
    String id;

    String fullName;

    String avatar;

    String role;

    Boolean isVerified;

    LocalDateTime lastSyncedAt;
}
