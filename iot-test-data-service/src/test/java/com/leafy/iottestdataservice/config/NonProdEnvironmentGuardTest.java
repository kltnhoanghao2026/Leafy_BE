package com.leafy.iottestdataservice.config;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NonProdEnvironmentGuardTest {

    @Test
    void validateProfilesBlocksProdProfile() {
        NonProdEnvironmentGuard guard = new NonProdEnvironmentGuard(null, new SeedProperties());

        assertThrows(IllegalStateException.class, () -> guard.validateProfiles(List.of("local", "prod")));
    }

    @Test
    void validateProfilesAllowsSupportedProfiles() {
        NonProdEnvironmentGuard guard = new NonProdEnvironmentGuard(null, new SeedProperties());

        assertDoesNotThrow(() -> guard.validateProfiles(List.of("staging")));
    }
}
