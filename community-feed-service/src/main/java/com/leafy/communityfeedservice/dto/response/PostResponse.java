package com.leafy.communityfeedservice.dto.response;

import com.leafy.communityfeedservice.model.embedded.*;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.Visibility;

import java.time.LocalDateTime;
import java.util.List;

public record PostResponse(
        String id,
        String authorId,
        String groupId,
        PostContent content,
        List<PostMedia> media,
        PostType postType,
        String sharedPostId,
        String originalAuthorId,
        PostContent sharedCaption,
        String rootPostId,
        LocalDateTime expiresAt,
        String musicId,
        List<String> viewerIds,
        LocationInfo location,
        List<StoryElement> elements,
        Visibility visibility,
        PostStats stats,
        LocalDateTime uploadedAt,
        LocalDateTime updatedAt,
        int version,
        boolean isCurrent,
        boolean isEdited
) {}
