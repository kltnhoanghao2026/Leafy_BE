package com.leafy.communityfeedservice.mapper;

import com.leafy.communityfeedservice.dto.request.PostCreateRequest;
import com.leafy.communityfeedservice.dto.request.PostUpdateRequest;
import com.leafy.communityfeedservice.dto.response.PostResponse;
import com.leafy.communityfeedservice.model.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "stats", ignore = true)
    Post toEntity(PostCreateRequest request);

    PostResponse toResponse(Post post);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "postType", ignore = true)
    void updateEntityFromRequest(PostUpdateRequest request, @MappingTarget Post post);
}
