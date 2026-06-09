package com.leafy.searchservice.mapper;

import com.leafy.searchservice.dto.response.FailedEventResponse;
import com.leafy.searchservice.model.mongo.FailedEvents;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FailedEventMapper {
    FailedEventResponse toDto(FailedEvents source);
}
