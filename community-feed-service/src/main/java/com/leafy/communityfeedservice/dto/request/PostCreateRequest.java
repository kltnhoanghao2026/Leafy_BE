package com.leafy.communityfeedservice.dto.request;

import com.leafy.communityfeedservice.model.embedded.LocationInfo;
import com.leafy.communityfeedservice.model.embedded.PostContent;
import com.leafy.communityfeedservice.model.embedded.PostMedia;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.Visibility;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PostCreateRequest(
        String groupId,
        @NotNull
        PostContent content,
        List<PostMedia> media,
        @NotNull
        PostType postType,
        String sharedPostId,
        String originalAuthorId,
        PostContent sharedCaption,
        String rootPostId,
        LocationInfo location,
        @NotNull
        Visibility visibility
) {}
