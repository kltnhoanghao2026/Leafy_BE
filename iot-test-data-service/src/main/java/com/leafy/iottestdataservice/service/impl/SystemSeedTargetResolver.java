package com.leafy.iottestdataservice.service.impl;

import com.leafy.iottestdataservice.client.FarmSeedClient;
import com.leafy.iottestdataservice.client.ProfileSeedClient;
import com.leafy.iottestdataservice.client.dto.FarmPlotResponse;
import com.leafy.iottestdataservice.client.dto.FarmZoneResponse;
import com.leafy.iottestdataservice.client.dto.ProfileResponse;
import com.leafy.iottestdataservice.config.SeedProperties;
import com.leafy.iottestdataservice.dto.BootstrapRequest;
import com.leafy.iottestdataservice.model.SeedTarget;
import com.leafy.iottestdataservice.service.SeedTargetResolver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemSeedTargetResolver implements SeedTargetResolver {

    private final SeedProperties seedProperties;
    private final ProfileSeedClient profileSeedClient;
    private final FarmSeedClient farmSeedClient;

    @Override
    public List<SeedTarget> resolveTargets(BootstrapRequest request, int maxTargets) {
        List<ProfileResponse> profiles = resolveProfiles(request);
        Set<String> requestedFarmPlotIds = toSet(request == null ? null : request.farmPlotIds());
        Set<String> requestedZoneIds = toSet(request == null ? null : request.zoneIds());

        List<SeedTarget> targets = new ArrayList<>();
        for (ProfileResponse profile : profiles) {
            if (isBlank(profile.id()) || isBlank(profile.userId())) {
                continue;
            }
            List<FarmPlotResponse> plots = farmSeedClient.getFarmPlots(profile.id()).stream()
                .filter(plot -> requestedFarmPlotIds.isEmpty() || requestedFarmPlotIds.contains(plot.id()))
                .filter(this::isActivePlot)
                .toList();

            for (FarmPlotResponse plot : plots) {
                List<FarmZoneResponse> zones = farmSeedClient.getFarmZones(plot.id()).stream()
                    .filter(zone -> requestedZoneIds.isEmpty() || requestedZoneIds.contains(zone.id()))
                    .filter(this::isActiveZone)
                    .toList();

                for (FarmZoneResponse zone : zones) {
                    targets.add(new SeedTarget(profile.userId(), profile.id(), plot.id(), zone.id()));
                    if (targets.size() >= maxTargets) {
                        return List.copyOf(targets);
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            throw new IllegalStateException(
                "No real farm zones were found for IoT bootstrap. Create active profiles, farm plots and zones first, "
                    + "or pass userIds/profileIds/farmPlotIds/zoneIds in the bootstrap request."
            );
        }
        return List.copyOf(targets);
    }

    private List<ProfileResponse> resolveProfiles(BootstrapRequest request) {
        Set<String> profileIds = toSet(request == null ? null : request.profileIds());
        Set<String> userIds = toSet(request == null ? null : request.userIds());

        if (!profileIds.isEmpty()) {
            return profileIds.stream()
                .map(profileSeedClient::getProfileById)
                .flatMap(OptionalUtil::stream)
                .filter(this::isActiveProfile)
                .toList();
        }

        if (!userIds.isEmpty()) {
            return userIds.stream()
                .map(profileSeedClient::getProfileByUserId)
                .flatMap(OptionalUtil::stream)
                .filter(this::isActiveProfile)
                .toList();
        }

        return profileSeedClient.getActiveProfiles(0, seedProperties.getProfile().getPageSize()).stream()
            .filter(this::isActiveProfile)
            .toList();
    }

    private boolean isActiveProfile(ProfileResponse profile) {
        return profile != null && (profile.active() == null || profile.active());
    }

    private boolean isActivePlot(FarmPlotResponse plot) {
        return plot != null && (plot.status() == null || Objects.equals(plot.status(), "ACTIVE"));
    }

    private boolean isActiveZone(FarmZoneResponse zone) {
        return zone != null && (zone.status() == null || Objects.equals(zone.status(), "ACTIVE"));
    }

    private Set<String> toSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
            .filter(value -> !isBlank(value))
            .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class OptionalUtil {
        private static <T> java.util.stream.Stream<T> stream(java.util.Optional<T> optional) {
            return optional.stream();
        }
    }
}
