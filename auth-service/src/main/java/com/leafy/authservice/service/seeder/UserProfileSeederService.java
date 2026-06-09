package com.leafy.authservice.service.seeder;

import com.leafy.authservice.dto.response.UserProfileSeederResponse;

public interface UserProfileSeederService {
    UserProfileSeederResponse seedUsersAndProfiles(int quantity);
}