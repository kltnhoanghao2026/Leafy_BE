package com.leafy.communityfeedservice.dto.request;

import com.leafy.communityfeedservice.model.embedded.PostContent;
import com.leafy.communityfeedservice.model.embedded.PostMedia;
import com.leafy.communityfeedservice.model.enums.Visibility;

import java.util.List;

public record PostUpdateRequest(
        PostContent content,
        List<PostMedia> media,
        PostContent sharedCaption,
        Visibility visibility,
        String planId
) {}
