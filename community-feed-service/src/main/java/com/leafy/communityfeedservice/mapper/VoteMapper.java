package com.leafy.communityfeedservice.mapper;

import com.leafy.communityfeedservice.dto.response.VoteResponse;
import com.leafy.communityfeedservice.model.Vote;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VoteMapper {
    @Mapping(target = "authorInfo", ignore = true)
    VoteResponse toResponse(Vote vote);
}