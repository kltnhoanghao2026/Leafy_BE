package com.leafy.authservice.service.seeder;

import com.leafy.authservice.client.ProfileServiceClient;
import com.leafy.authservice.client.dto.ProfileCreateRequest;
import com.leafy.authservice.dto.response.UserProfileSeederResponse;
import com.leafy.authservice.model.User;
import com.leafy.authservice.repository.UserRepository;
import com.leafy.common.enums.Role;
import com.leafy.common.exception.AppException;
import com.leafy.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Seeds auth users and related profiles using the same persistence flow as
 * registration, excluding OTP verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileSeederServiceImpl implements UserProfileSeederService {

    static final int MIN_QUANTITY = 1;
    static final int MAX_QUANTITY = 1000;
    static final String DEFAULT_PASSWORD = "Seed@12345";

    static final String[] PROVINCE_CODES = {
            "01", "79", "31", "92", "48", "56", "74", "60", "52", "38"
    };
    static final String[] DISTRICT_CODES = {
            "001", "002", "003", "004", "005", "006", "007", "008", "009", "010"
    };
    static final String[] WARD_CODES = {
            "00001", "00003", "00005", "00007", "00009", "00025", "00028", "00031", "00034", "00037"
    };
    static final String[] ADDRESS_LINES = {
            "123 Nong Nghiep Street", "456 Dong Ruong Street", "789 Canh Tac Street",
            "101 Vuon Rau Street", "202 Khu Nha Kinh Street", "303 Cao Nguyen Street",
            "404 Dong Bang Street", "505 Ven Song Street", "606 Chan Nui Street", "707 Dat Bai Street"
    };
    static final double[][] GEO_COORDS = {
            {21.0278, 105.8342}, {10.7769, 106.7009}, {16.0478, 108.2208},
            {10.0452, 105.7469}, {15.1214, 108.8011}, {11.9464, 108.4419},
            {10.9574, 108.3025}, {20.8135, 106.6878}
    };
    static final String[] SPECIALTIES = {
            "Rice Cultivation", "Vegetable Farming", "Fruit Orchards", "Coffee Plantation",
            "Herb Growing", "Flower Cultivation", "Organic Farming", "Aquaculture"
    };

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    ProfileServiceClient profileServiceClient;

    @Override
    public UserProfileSeederResponse seedUsersAndProfiles(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }

        int createdUserCount = 0;
        int createdProfileCount = 0;
        int failedUserCount = 0;
        int failedProfileCount = 0;

        long batchSeed = System.currentTimeMillis();

        for (int i = 0; i < quantity; i++) {
            User savedUser;

            try {
                savedUser = userRepository.save(buildSeedUser(batchSeed, i));
                createdUserCount++;
            } catch (Exception e) {
                failedUserCount++;
                log.warn("Failed to create seed user at index {}: {}", i, e.getMessage());
                continue;
            }

            try {
                int mod = i % 10;
                int geoIdx = i % GEO_COORDS.length;
                ProfileCreateRequest profileRequest = ProfileCreateRequest.builder()
                        .userId(savedUser.getId())
                        .fullName(savedUser.getEmail().split("@")[0])
                        .role(i % 3 == 0 ? "EXPERT" : "FARMER")
                        .specialty(SPECIALTIES[i % SPECIALTIES.length])
                        .addressLine(ADDRESS_LINES[mod])
                        .provinceCode(PROVINCE_CODES[mod])
                        .districtCode(DISTRICT_CODES[mod])
                        .wardCode(WARD_CODES[mod])
                        .latitude(GEO_COORDS[geoIdx][0])
                        .longitude(GEO_COORDS[geoIdx][1])
                        .build();

                var profileResponse = profileServiceClient.createProfile(profileRequest);
                if (profileResponse != null && profileResponse.data() != null) {
                    createdProfileCount++;
                } else {
                    failedProfileCount++;
                }
            } catch (Exception e) {
                failedProfileCount++;
                log.warn("Failed to create profile for seed user {}: {}", savedUser.getId(), e.getMessage());
            }
        }

        log.info("Seed users/profiles completed - requested={}, usersCreated={}, profilesCreated={}, usersFailed={}, profilesFailed={}",
                quantity, createdUserCount, createdProfileCount, failedUserCount, failedProfileCount);

        return UserProfileSeederResponse.builder()
                .requestedCount(quantity)
                .createdUserCount(createdUserCount)
                .createdProfileCount(createdProfileCount)
                .failedUserCount(failedUserCount)
                .failedProfileCount(failedProfileCount)
                .build();
    }

    private User buildSeedUser(long batchSeed, int index) {
        String email = "seed.user." + batchSeed + "." + index + "@leafy.local";
        String phoneNumber = String.format("0%09d", Math.floorMod(batchSeed + index, 1_000_000_000L));

        return User.builder()
                .email(email)
                .phoneNumber(phoneNumber)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role(Role.USER)
                .build();
    }
}