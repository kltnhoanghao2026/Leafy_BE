package com.leafy.communityfeedservice.service.seeder;

import com.leafy.communityfeedservice.dto.response.SeederResponse;

public interface SeederService {
    SeederResponse reseedCommunityFeed(Integer postCount, Integer commentCount, Integer voteCount);
    SeederResponse syncProfileSummaries();
}
