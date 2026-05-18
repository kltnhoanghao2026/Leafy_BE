package com.leafy.plantmanagementservice.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebSearchResult {
    String title;
    String url;
    String content;
    Double score;
}
