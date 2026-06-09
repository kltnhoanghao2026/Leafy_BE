package com.leafy.plantmanagementservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SourceDocument {

    @JsonProperty("page_content")
    String pageContent;

    Map<String, Object> metadata;

    @JsonProperty("point_id")
    String pointId;

    public Object getMetadataValue(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}
