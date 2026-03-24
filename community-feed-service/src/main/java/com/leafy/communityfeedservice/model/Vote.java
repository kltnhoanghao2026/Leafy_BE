package com.leafy.communityfeedservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.Builder;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document("votes")
@CompoundIndex(name = "uniq_author_target", def = "{ 'authorId': 1, 'targetId': 1, 'targetType': 1 }", unique = true)
public class Vote extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    VoteType type;

    String authorId;

    String targetId;

    VoteTargetType targetType;
}
