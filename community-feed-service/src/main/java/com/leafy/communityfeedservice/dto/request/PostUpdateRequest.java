package com.leafy.communityfeedservice.dto.request;

import com.leafy.communityfeedservice.model.embedded.LocationInfo;
import com.leafy.communityfeedservice.model.embedded.PostContent;
import com.leafy.communityfeedservice.model.embedded.PostMedia;
import com.leafy.communityfeedservice.model.enums.Visibility;

import java.util.List;

public record PostUpdateRequest(
        PostContent content,
        List<PostMedia> media,
        PostContent sharedCaption,
        LocationInfo location,
        Visibility visibility
) {}
