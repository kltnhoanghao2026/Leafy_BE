package com.leafy.plantmanagementservice.dto.response.species;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SpeciesSeedResponse {
    Integer startPage;
    Integer pagesRequested;
    Integer perPage;
    Integer totalSaved;
    Integer createdCount;
    Integer updatedCount;
    Integer skippedCount;
    List<Integer> failedPages;
}
