package com.leafy.iottestdataservice.client;

import com.leafy.iottestdataservice.client.dto.ProfileResponse;
import java.util.List;
import java.util.Optional;

public interface ProfileSeedClient {

    List<ProfileResponse> getActiveProfiles(int page, int size);

    Optional<ProfileResponse> getProfileById(String profileId);

    Optional<ProfileResponse> getProfileByUserId(String userId);
}
