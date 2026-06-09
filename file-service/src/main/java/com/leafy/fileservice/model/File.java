package com.leafy.fileservice.model;

import com.leafy.common.model.BaseModel;
import com.leafy.fileservice.model.enums.FileType;
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
    FileType fileType;

    long fileSize;

    String uploadedBy;

}
