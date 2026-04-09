package com.leafy.common.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
public class BaseModel {

    @CreatedDate
    LocalDateTime createdAt;

    @LastModifiedDate
    LocalDateTime lastModifiedAt;

    @CreatedBy
    String createdBy;

    @LastModifiedBy
    String lastModifiedBy;

    Boolean active = true;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
