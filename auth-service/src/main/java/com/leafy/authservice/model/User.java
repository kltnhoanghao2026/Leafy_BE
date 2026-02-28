package com.leafy.authservice.model;

import com.leafy.common.enums.Role;
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
@Document("user")
public class User extends BaseModel {

    @MongoId(FieldType.OBJECT_ID)
    String id;

    String email;
    String phoneNumber;

    String password;

    Role role;
}
