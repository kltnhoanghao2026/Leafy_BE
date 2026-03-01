package com.leafy.fileservice.model;

import com.leafy.common.model.BaseModel;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = false)
@Document("file")
public class File extends BaseModel {
    @MongoId(FieldType.OBJECT_ID)
    String id;

    String s3Key;
    String originalFileName;
    String contentType;

    long fileSize;

    String uploadedBy;

}
