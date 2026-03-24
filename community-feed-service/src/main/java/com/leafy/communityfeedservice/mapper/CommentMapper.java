package com.leafy.communityfeedservice.mapper;

import com.leafy.communityfeedservice.dto.request.CommentCreateRequest;
import com.leafy.communityfeedservice.dto.request.CommentUpdateRequest;
import com.leafy.communityfeedservice.dto.response.CommentResponse;
import com.leafy.communityfeedservice.model.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.Builder;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true))
public interface CommentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "replyCount", ignore = true)
    @Mapping(target = "replyDepth", ignore = true)
    @Mapping(target = "upvoteCount", ignore = true)
    @Mapping(target = "downvoteCount", ignore = true)
    @Mapping(target = "edited", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    Comment toEntity(CommentCreateRequest request);

    CommentResponse toResponse(Comment comment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "postId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "parentId", ignore = true)
    @Mapping(target = "replyCount", ignore = true)
    @Mapping(target = "replyDepth", ignore = true)
    @Mapping(target = "upvoteCount", ignore = true)
    @Mapping(target = "downvoteCount", ignore = true)
    @Mapping(target = "edited", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntityFromRequest(CommentUpdateRequest request, @MappingTarget Comment comment);
}
