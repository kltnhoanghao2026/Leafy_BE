package com.leafy.plantmanagementservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SourceDocument {

    @JsonProperty("title")
    String title;

    @JsonProperty("content")
    String content;

    @JsonProperty("url")
    String url;

    @JsonProperty("score")
    Double score;
}
