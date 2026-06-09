package com.leafy.authservice.config;

import com.leafy.authservice.client.ProfileServiceClient;
import com.leafy.authservice.client.dto.ProfileCreateRequest;
import com.leafy.authservice.model.User;
import com.leafy.authservice.repository.UserRepository;
import com.leafy.common.enums.ProfileRole;
import com.leafy.common.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Initializes the default admin user on service startup.
 * Idempotent: skips creation if the admin account already exists.
 * On first run it also creates the admin profile and caches the profileId
 * on the User document so JWT generation never needs a cross-service call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    @Value("${app.admin.email:admin@leafy.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@12345}")
    private String adminPassword;

    @Value("${app.admin.phone:0900000000}")
    private String adminPhone;

    @Value("${app.admin.full-name:System Administrator}")
    private String adminFullName;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileServiceClient profileServiceClient;

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findByEmail(adminEmail).ifPresentOrElse(
                existingUser -> {
                    log.info("Admin user '{}' already exists, checking profileId cache", adminEmail);
                    backfillProfileIdIfMissing(existingUser);
                },
                () -> {
                    log.info("Admin user not found — creating admin account: {}", adminEmail);

                    User adminUser = User.builder()
                            .email(adminEmail)
                            .phoneNumber(adminPhone)
                            .password(passwordEncoder.encode(adminPassword))
                            .role(Role.ADMIN)
                            .build();

                    User savedUser = userRepository.save(adminUser);
                    log.info("Admin user created with ID: {}", savedUser.getId());

                    createAdminProfileAndCacheId(savedUser);
                }
        );
    }

    /**
     * Called on first boot to create the profile and cache its ID on the User doc.
     */
    private void createAdminProfileAndCacheId(User adminUser) {
        try {
            var response = profileServiceClient.createProfile(
                    ProfileCreateRequest.builder()
                            .userId(adminUser.getId())
                            .fullName(adminFullName)
                            .build()
            );

            if (response != null && response.data() != null) {
                String profileId = response.data().getId();
                adminUser.setProfileId(profileId);
                userRepository.save(adminUser);
                log.info("Admin profile created and profileId '{}' cached on User '{}'",
                        profileId, adminUser.getId());
            } else {
                log.warn("Admin profile creation returned empty response — profileId not cached");
            }
        } catch (Exception ex) {
            log.warn("Admin profile creation failed (profile-service may not be ready yet): {}",
                    ex.getMessage());
        }
    }

    /**
     * Back-fills profileId for users seeded before this change was introduced
     * (i.e. an existing admin User doc whose profileId is still null).
     */
    private void backfillProfileIdIfMissing(User adminUser) {
        if (adminUser.getProfileId() != null) {
            return; // already cached — nothing to do
        }

        log.info("profileId missing on existing admin '{}' — attempting back-fill", adminEmail);
        try {
            var response = profileServiceClient.getProfileByUserId(adminUser.getId());
            if (response != null && response.data() != null) {
                String profileId = response.data().getId();
                adminUser.setProfileId(profileId);
                userRepository.save(adminUser);
                log.info("Back-filled profileId '{}' for admin user '{}'", profileId, adminUser.getId());
            } else {
                log.warn("No profile found for existing admin '{}' — profileId stays null", adminEmail);
            }
        } catch (Exception ex) {
            log.warn("Back-fill of profileId failed for admin '{}': {}", adminEmail, ex.getMessage());
        }
    }
}
