package com.leafy.farmservice.service.seeder;

import com.leafy.common.security.UserPrincipal;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.farmservice.client.ProfileServiceClient;
import com.leafy.farmservice.client.dto.ExternalApiResponse;
import com.leafy.farmservice.client.dto.PagedResponse;
import com.leafy.farmservice.client.dto.ProfileSummary;
import com.leafy.farmservice.config.FarmSeederProperties;
import com.leafy.farmservice.dto.response.seeder.FarmSeederResponse;
import com.leafy.farmservice.model.FarmPlot;
import com.leafy.farmservice.model.FarmZone;
import com.leafy.farmservice.model.enums.FarmPlotStatus;
import com.leafy.farmservice.model.enums.FarmZoneStatus;
import com.leafy.farmservice.repository.FarmPlotRepository;
import com.leafy.farmservice.repository.FarmZoneRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FarmSeederServiceImpl implements FarmSeederService {

    FarmPlotRepository farmPlotRepository;
    FarmZoneRepository farmZoneRepository;
    ProfileServiceClient profileServiceClient;
    FarmSeederProperties seederProperties;

    // Vietnamese province codes (63 provinces; representative sample)
    private static final String[] PROVINCE_CODES = {
        "01", "79", "31", "92", "48", "56", "74", "60", "52", "38"
    };

    // Simplified district + ward codes per province index
    private static final String[] DISTRICT_CODES = {
        "001", "002", "003", "004", "005", "006", "007", "008", "009", "010"
    };

    private static final String[] WARD_CODES = {
        "00001", "00003", "00005", "00007", "00009", "00025", "00028", "00031", "00034", "00037"
    };

    private static final String[] ADDRESS_PREFIXES = {
        "123 Nong Nghiep", "456 Dong Ruong", "789 Canh Tac", "101 Vuon Rau",
        "202 Khu Nha Kinh", "303 Cao Nguyen", "404 Dong Bang", "505 Ven Song",
        "606 Chan Nui", "707 Dat Bai"
    };

    private static final String[] PLOT_NAME_TEMPLATES = {
        "Organic Field %s", "Highland Farm %s", "Riverside Plot %s", "Valley Garden %s",
        "Plateau Field %s", "Hill Farm %s", "Delta Plot %s", "Forest Edge %s",
        "Terrace Farm %s", "Wetland Zone %s"
    };

    private static final BigDecimal[] AREA_VALUES = {
        BigDecimal.valueOf(500.0), BigDecimal.valueOf(1000.5), BigDecimal.valueOf(1500.0),
        BigDecimal.valueOf(2000.0), BigDecimal.valueOf(2500.5), BigDecimal.valueOf(3000.0),
        BigDecimal.valueOf(750.25), BigDecimal.valueOf(1200.0), BigDecimal.valueOf(4000.0),
        BigDecimal.valueOf(800.75)
    };

    // {lat, lon} pairs for major VN agricultural regions
    private static final double[][] GEO_COORDS = {
        {21.0278, 105.8342},  // Hanoi
        {10.7769, 106.7009},  // Ho Chi Minh City
        {16.0478, 108.2208},  // Da Nang
        {10.0452, 105.7469},  // Can Tho
        {15.1214, 108.8011},  // Quang Nam
        {11.9464, 108.4419},  // Da Lat
        {10.9574, 108.3025},  // Phan Thiet
        {20.8135, 106.6878},  // Hai Phong
    };

    private static final String[] ZONE_NAMES = {"Zone A", "Zone B", "Zone C", "Zone D", "Zone E"};

    private static final String[] SOIL_TYPES = {"CLAY", "SANDY", "LOAMY", "SILTY", "PEATY"};

    private static final String[] CROP_TYPES = {"RICE", "VEGETABLES", "FRUIT", "HERBS", "FLOWERS", "COFFEE"};

    private static final LocalDate[] PLANTING_DATES = {
        LocalDate.of(2024, 3, 15),
        LocalDate.of(2024, 6, 1),
        LocalDate.of(2024, 9, 20),
        LocalDate.of(2025, 1, 5),
        LocalDate.of(2025, 4, 10),
        null
    };

    private static final BigDecimal[] ELEVATION_VALUES = {
        BigDecimal.valueOf(5.5), BigDecimal.valueOf(12.0),
        BigDecimal.valueOf(25.5), BigDecimal.valueOf(50.0),
        BigDecimal.valueOf(8.0), null
    };

    @Override
    @Transactional
    public FarmSeederResponse reseed(Integer plotsPerProfile, Integer zonesPerPlot) {
        int effectivePlotsPerProfile = plotsPerProfile != null ? plotsPerProfile : seederProperties.getPlotsPerProfile();
        int effectiveZonesPerPlot = zonesPerPlot != null ? zonesPerPlot : seederProperties.getZonesPerPlot();

        UserPrincipal currentUser = ServiceSecurityUtils.getCurrentUser();
        List<String> profileIds = fetchProfileIds(currentUser);

        log.info("Farm seeder: found {} profiles, seeding {} plots each with {} zones",
                profileIds.size(), effectivePlotsPerProfile, effectiveZonesPerPlot);

        long deletedZoneCount = farmZoneRepository.count();
        long deletedPlotCount = farmPlotRepository.count();

        // Delete zones before plots to respect referential order
        farmZoneRepository.deleteAll();
        farmPlotRepository.deleteAll();

        List<FarmPlot> seededPlots = seedFarmPlots(profileIds, effectivePlotsPerProfile);
        List<FarmZone> seededZones = seedFarmZones(seededPlots, effectiveZonesPerPlot);

        log.info("Farm seeder complete: seeded {} plots, {} zones from {} profiles",
                seededPlots.size(), seededZones.size(), profileIds.size());

        return FarmSeederResponse.builder()
                .deletedPlotCount(deletedPlotCount)
                .deletedZoneCount(deletedZoneCount)
                .seededPlotCount(seededPlots.size())
                .seededZoneCount(seededZones.size())
                .sourceProfileCount(profileIds.size())
                .build();
    }

    private List<String> fetchProfileIds(UserPrincipal currentUser) {
        Set<String> profileIds = new LinkedHashSet<>();

        String roleHeader = currentUser.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.joining(","));

        for (int page = 0; page < seederProperties.getProfileMaxPages(); page++) {
            ExternalApiResponse<PagedResponse<ProfileSummary>> response = profileServiceClient.getActiveProfiles(
                    page,
                    seederProperties.getProfilePageSize(),
                    "createdAt",
                    "DESC",
                    currentUser.getUserId(),
                    currentUser.getEmail(),
                    roleHeader,
                    currentUser.getProfileId());

            if (response == null || response.getData() == null || response.getData().getContent() == null) {
                break;
            }

            List<String> pageIds = response.getData().getContent().stream()
                    .map(ProfileSummary::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            profileIds.addAll(pageIds);

            if (pageIds.isEmpty() || page + 1 >= response.getData().getTotalPages()) {
                break;
            }
        }

        return new ArrayList<>(profileIds);
    }

    private List<FarmPlot> seedFarmPlots(List<String> profileIds, int plotsPerProfile) {
        List<FarmPlot> plots = new ArrayList<>();
        int plotIndex = 0;

        for (String profileId : profileIds) {
            for (int p = 0; p < plotsPerProfile; p++) {
                FarmPlot plot = buildFarmPlot(profileId, plotIndex);
                plots.add(plot);
                plotIndex++;
            }
        }

        return farmPlotRepository.saveAll(plots);
    }

    private FarmPlot buildFarmPlot(String ownerProfileId, int index) {
        int mod10 = index % 10;
        FarmPlotStatus status;
        if (mod10 < 7) {
            status = FarmPlotStatus.ACTIVE;
        } else if (mod10 < 9) {
            status = FarmPlotStatus.INACTIVE;
        } else {
            status = FarmPlotStatus.ARCHIVED;
        }

        String nameLetter = String.valueOf((char) ('A' + (index % 26)));
        String name = String.format(PLOT_NAME_TEMPLATES[index % PLOT_NAME_TEMPLATES.length], nameLetter);

        String provinceCode = PROVINCE_CODES[index % PROVINCE_CODES.length];
        String districtCode = DISTRICT_CODES[index % DISTRICT_CODES.length];
        String wardCode = WARD_CODES[index % WARD_CODES.length];
        String addressLine = ADDRESS_PREFIXES[index % ADDRESS_PREFIXES.length] + " Street";
        BigDecimal areaM2 = AREA_VALUES[index % AREA_VALUES.length];

        // ~70% have geo coordinates
        Double latitude = null;
        Double longitude = null;
        Map<String, Object> boundaryGeojson = null;

        if (index % 10 < 7) {
            double[] coords = GEO_COORDS[index % GEO_COORDS.length];
            latitude = coords[0];
            longitude = coords[1];
            boundaryGeojson = buildBoundaryGeojson(latitude, longitude, areaM2);
        }

        FarmPlot plot = new FarmPlot();
        plot.setOwnerProfileId(ownerProfileId);
        plot.setName(name);
        plot.setCode(generateSeedCode(index));
        plot.setDescription("Seeded farm plot #" + (index + 1) + " for development and QA testing");
        plot.setAreaM2(areaM2);
        plot.setAddressLine(addressLine);
        plot.setProvinceCode(provinceCode);
        plot.setDistrictCode(districtCode);
        plot.setWardCode(wardCode);
        plot.setLatitude(latitude);
        plot.setLongitude(longitude);
        plot.setBoundaryGeojson(boundaryGeojson);
        plot.setStatus(status);
        plot.setActive(true);
        return plot;
    }

    private List<FarmZone> seedFarmZones(List<FarmPlot> plots, int zonesPerPlot) {
        List<FarmZone> zones = new ArrayList<>();
        int globalZoneIndex = 0;

        for (FarmPlot plot : plots) {
            // Skip ARCHIVED plots — no active zones make sense for them
            if (plot.getStatus() == FarmPlotStatus.ARCHIVED) {
                continue;
            }

            for (int z = 0; z < zonesPerPlot; z++) {
                FarmZone zone = buildFarmZone(plot, z, globalZoneIndex);
                zones.add(zone);
                globalZoneIndex++;
            }
        }

        return farmZoneRepository.saveAll(zones);
    }

    private FarmZone buildFarmZone(FarmPlot plot, int zoneIndex, int globalIndex) {
        // Zone names guaranteed unique per plot by using fixed slot names
        String zoneName = ZONE_NAMES[zoneIndex % ZONE_NAMES.length];

        // Status distribution: 0-2→ACTIVE, 3→INACTIVE, 4→ARCHIVED (per globalIndex cycle)
        int mod5 = globalIndex % 5;
        FarmZoneStatus status;
        if (mod5 < 3) {
            status = FarmZoneStatus.ACTIVE;
        } else if (mod5 == 3) {
            status = FarmZoneStatus.INACTIVE;
        } else {
            status = FarmZoneStatus.ARCHIVED;
        }

        // Inherit plot status degradation: INACTIVE plot → at most INACTIVE zones
        if (plot.getStatus() == FarmPlotStatus.INACTIVE && status == FarmZoneStatus.ACTIVE) {
            status = FarmZoneStatus.INACTIVE;
        }

        String soilType = SOIL_TYPES[globalIndex % SOIL_TYPES.length];
        String cropType = CROP_TYPES[globalIndex % CROP_TYPES.length];
        LocalDate plantingDate = PLANTING_DATES[globalIndex % PLANTING_DATES.length];
        BigDecimal elevationM = ELEVATION_VALUES[globalIndex % ELEVATION_VALUES.length];

        // Zone area is a fraction of plot area
        BigDecimal zoneArea = plot.getAreaM2() != null
                ? plot.getAreaM2().divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.valueOf(200.0);

        String zoneCode = "ZN-" + plot.getCode().replace("FP-", "") + "-" + (char) ('A' + zoneIndex);
        String description = String.format("Seeded zone '%s' in %s — soil: %s, crop: %s",
                zoneName, plot.getName(), soilType, cropType);

        FarmZone zone = new FarmZone();
        zone.setFarmPlotId(plot.getId());
        zone.setZoneName(zoneName);
        zone.setZoneCode(zoneCode);
        zone.setDescription(description);
        zone.setAreaM2(zoneArea);
        zone.setSoilType(soilType);
        zone.setCropType(cropType);
        zone.setPlantingDate(plantingDate);
        zone.setElevationM(elevationM);
        zone.setStatus(status);
        zone.setActive(true);
        return zone;
    }

    private String generateSeedCode(int index) {
        return String.format("FP-SEED-%05d", index + 1);
    }

    private Map<String, Object> buildBoundaryGeojson(double lat, double lon, BigDecimal areaM2) {
        // Approximate degree offset for a ~100m boundary
        double offset = 0.0005;
        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", List.of(List.of(
                List.of(lon - offset, lat - offset),
                List.of(lon + offset, lat - offset),
                List.of(lon + offset, lat + offset),
                List.of(lon - offset, lat + offset),
                List.of(lon - offset, lat - offset)
        )));

        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", Map.of("area_m2", areaM2 != null ? areaM2.doubleValue() : 0.0));
        return feature;
    }
}
