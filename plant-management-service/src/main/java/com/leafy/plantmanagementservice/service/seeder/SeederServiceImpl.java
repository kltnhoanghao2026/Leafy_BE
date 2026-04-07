package com.leafy.plantmanagementservice.service.seeder;

import com.leafy.common.security.UserPrincipal;
import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.client.FarmServiceClient;
import com.leafy.plantmanagementservice.client.dto.ExternalApiResponse;
import com.leafy.plantmanagementservice.client.dto.FarmPlotSummary;
import com.leafy.plantmanagementservice.client.dto.FarmZoneSummary;
import com.leafy.plantmanagementservice.config.SeederProperties;
import com.leafy.plantmanagementservice.dto.response.seeder.PlantSeederResponse;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.Species;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.repository.SpeciesRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
public class SeederServiceImpl implements SeederService {

    SpeciesRepository speciesRepository;
    PlantRepository plantRepository;
    PlantEventRepository plantEventRepository;
    FarmServiceClient farmServiceClient;
    SeederProperties seederProperties;

    // -------------------------------------------------------------------------
    // Species seed data
    // -------------------------------------------------------------------------
    // [commonName, cultivarName|null, lightRequirements, plantingSeason, plantingWindow,
    //  waterFreqDays, daysToMaturity, spacing, expectedYieldKg|null, diseaseIds|null]
    private static final Object[][] SPECIES_DATA = {
        {"Coffee Arabica",    "Caturra",      "partial_shade",   "Spring",         "Spring-Summer", 3, 730,  1.5,  2.5, new String[]{"DIS-LEAF-RUST", "DIS-CBD"}},
        {"Coffee Robusta",    "Conillon",     "full_sun",        "Summer",         "Year-round",    2, 365,  2.0,  3.5, new String[]{"DIS-WILT"}},
        {"Coffee Liberica",   null,           "partial_shade",   "Spring-Summer",  "Spring",        3, 900,  2.5,  2.0, null},
        {"Tomato",            null,           "full_sun",        "Spring-Summer",  "Spring",        2, 70,   0.5,  5.0, new String[]{"DIS-BLIGHT", "DIS-MOSAIC"}},
        {"Cherry Tomato",     "Sweet 100",    "full_sun",        "Spring",         "Spring",        2, 65,   0.4,  3.0, null},
        {"Bell Pepper",       null,           "full_sun",        "Spring-Summer",  "Spring",        2, 80,   0.5,  4.0, null},
        {"Chili Pepper",      "Thai Hot",     "full_sun",        "Summer",         "Spring-Summer", 3, 75,   0.45, 2.5, new String[]{"DIS-ANTHRACNOSE"}},
        {"Basil",             "Genovese",     "full_sun",        "Spring",         "Spring-Summer", 1, 25,   0.25, 0.5, null},
        {"Lettuce",           null,           "partial_shade",   "Autumn",         "Spring-Autumn", 1, 30,   0.2,  1.0, null},
        {"Spinach",           null,           "partial_shade",   "Winter",         "Autumn-Winter", 1, 40,   0.15, 1.5, null},
        {"Cucumber",          null,           "full_sun",        "Summer",         "Spring-Summer", 2, 55,   0.6,  8.0, null},
        {"Pumpkin",           "Sugar Pie",    "full_sun",        "Spring",         "Spring",        5, 100,  1.0,  10.0, null},
        {"Green Bean",        null,           "full_sun",        "Summer",         "Spring-Summer", 2, 55,   0.2,  3.0, null},
        {"Lemongrass",        null,           "full_sun",        "Spring",         "Spring",        3, 90,   0.5,  4.0, null},
        {"Mint",              "Spearmint",    "indirect_light",  "Spring-Autumn",  "Spring",        1, 30,   0.3,  1.0, null},
    };

    // idealEnv data parallel to SPECIES_DATA
    private static final int[] PERENUAL_IDS = {100, 101, 102, 200, 201, 202, 203, 300, 400, 401, 500, 600, 700, 800, 900};
    private static final String[] WATERING_LEVELS = {"moderate", "frequent", "moderate", "frequent", "frequent",
            "moderate", "moderate", "frequent", "frequent", "moderate",
            "frequent", "moderate", "moderate", "moderate", "frequent"};

    // -------------------------------------------------------------------------
    // Plant seed variants
    // -------------------------------------------------------------------------
    private static final String[] SOURCE_TYPES = {"SEED", "CUTTING", "SEEDLING", "GRAFT"};

    private static final EventType[] ALL_EVENT_TYPES = EventType.values();

    // Per event-type notes/descriptions
    private static final Map<EventType, String[]> EVENT_NOTES = new HashMap<>() {{
        put(EventType.IRRIGATION,             new String[]{"Manual drip irrigation at 60% soil moisture", "Scheduled drip watering — 2L per plant"});
        put(EventType.NUTRITION,              new String[]{"Applied NPK 15-15-15 at 50g/plant", "Foliar spray with micronutrients"});
        put(EventType.WEED_CONTROL,           new String[]{"Hand-weeded around plant base", "Mechanical cultivation between rows"});
        put(EventType.PRUNING,                new String[]{"Removed dead branches and lateral shoots", "Topped plant to encourage bushiness"});
        put(EventType.SCOUTING,               new String[]{"Routine pest/disease observation — no issues found", "Weekly monitoring; minor aphid activity noted"});
        put(EventType.DISEASE_DETECTED,       new String[]{"Leaf rust detected on lower canopy", "Powdery mildew observed on upper leaves"});
        put(EventType.TREATMENT_APPLICATION,  new String[]{"Fungicide (copper hydroxide) applied at 3g/L", "Insecticide (neem oil) spray at 5ml/L"});
        put(EventType.QUARANTINE,             new String[]{"Plant isolated due to suspected pest infestation", "Quarantined: awaiting disease confirmation"});
        put(EventType.HEALTH_RECOVERY,        new String[]{"Plant recovering well after treatment", "New healthy growth observed post-quarantine"});
        put(EventType.PHENOLOGY,              new String[]{"First flower bud visible — reproductive stage begins", "50% flowering; pollination in progress"});
        put(EventType.REPOT,                  new String[]{"Transplanted to 20L container with fresh compost", "Uprooted and replanted in enriched soil"});
        put(EventType.HARVEST,                new String[]{"Selective harvest of mature fruits", "Full canopy harvest — end of productive cycle"});
    }};

    @Override
    @Transactional
    public PlantSeederResponse reseed(Integer speciesCount, Integer plantCount, Integer eventsPerPlant) {
        int effectiveSpeciesCount = speciesCount != null ? speciesCount : seederProperties.getSpeciesCount();
        int effectivePlantCount = plantCount != null ? plantCount : seederProperties.getPlantCount();
        int effectiveEventsPerPlant = eventsPerPlant != null ? eventsPerPlant : seederProperties.getEventsPerPlant();

        UserPrincipal currentUser = ServiceSecurityUtils.getCurrentUser();
        String roleHeader = currentUser.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.joining(","));

        // Fetch farm data for referential integrity
        List<FarmPlotSummary> farmPlots = fetchFarmPlots(currentUser, roleHeader);
        List<FarmZoneSummary> farmZones = fetchFarmZones(currentUser, roleHeader);

        log.info("Plant seeder: {} farmPlots, {} farmZones available. Seeding {} species, {} plants, {} events/plant",
                farmPlots.size(), farmZones.size(), effectiveSpeciesCount, effectivePlantCount, effectiveEventsPerPlant);

        // Build farmPlotId -> List<zoneId> lookup
        Map<String, List<String>> zonesByPlot = farmZones.stream()
                .filter(z -> z.getFarmPlotId() != null)
                .collect(Collectors.groupingBy(
                        FarmZoneSummary::getFarmPlotId,
                        Collectors.mapping(FarmZoneSummary::getId, Collectors.toList())));

        // Collect plot IDs that have at least one zone (for varied zone assignment)
        List<String> farmPlotIds = farmPlots.stream().map(FarmPlotSummary::getId).toList();

        Random random = new Random(seederProperties.getRandomSeed());

        // --- Species (upsert) ---
        int[] speciesCounts = seedSpecies(effectiveSpeciesCount);

        // Reload saved species IDs for plant FK assignment
        List<String> speciesIds = speciesRepository.findAll().stream()
                .map(Species::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        // --- Plants (wipe + reseed) ---
        long deletedPlantCount = plantRepository.count();
        long deletedEventCount = plantEventRepository.count();   // capture before deleting
        plantEventRepository.deleteAll();       // events must go first
        plantRepository.deleteAll();

        List<Plant> seededPlants = seedPlants(
                effectivePlantCount, speciesIds, farmPlotIds, zonesByPlot, random);

        // --- PlantEvents (wipe + reseed) ---
        List<PlantEvent> seededEvents = seedEvents(seededPlants, farmPlotIds, zonesByPlot,
                effectiveEventsPerPlant, random);

        log.info("Plant seeder complete: species={}, plants={}, events={}",
                speciesIds.size(), seededPlants.size(), seededEvents.size());

        return PlantSeederResponse.builder()
                .seededSpeciesCount(speciesCounts[0])
                .createdSpeciesCount(speciesCounts[1])
                .updatedSpeciesCount(speciesCounts[2])
                .deletedPlantCount(deletedPlantCount)
                .seededPlantCount(seededPlants.size())
                .deletedEventCount(deletedEventCount)
                .seededEventCount(seededEvents.size())
                .sourceFarmPlotCount(farmPlots.size())
                .sourceFarmZoneCount(farmZones.size())
                .build();
    }

    // -------------------------------------------------------------------------
    // Species seeding
    // -------------------------------------------------------------------------

    /** Upserts up to {@code count} species. Returns [totalSaved, created, updated]. */
    private int[] seedSpecies(int count) {
        int created = 0, updated = 0;
        int limit = Math.min(count, SPECIES_DATA.length);

        for (int i = 0; i < limit; i++) {
            Object[] row = SPECIES_DATA[i];
            String commonName = (String) row[0];

            Species species = speciesRepository.findByCommonNameIgnoreCase(commonName)
                    .orElse(new Species());

            boolean isNew = species.getId() == null;

            species.setCommonName(commonName);
            species.setCultivarName((String) row[1]);
            species.setLightRequirements((String) row[2]);
            species.setPlantingSeason((String) row[3]);
            species.setPlantingWindow((String) row[4]);
            species.setWaterFrequencyDays((Integer) row[5]);
            species.setDaysToMaturity((Integer) row[6]);
            species.setSpacing((Double) row[7]);
            species.setExpectedYieldKg(row[8] != null ? (Double) row[8] : null);

            String[] diseaseIds = (String[]) row[9];
            species.setCommonDiseaseIds(diseaseIds != null ? List.of(diseaseIds) : List.of());

            species.setIdealEnv(buildIdealEnv(i));
            species.setActive(true);

            speciesRepository.save(species);

            if (isNew) created++;
            else updated++;
        }

        return new int[]{limit, created, updated};
    }

    private Map<String, Object> buildIdealEnv(int index) {
        Map<String, Object> env = new HashMap<>();
        env.put("perenualId", PERENUAL_IDS[index % PERENUAL_IDS.length]);
        env.put("watering", WATERING_LEVELS[index % WATERING_LEVELS.length]);
        env.put("sunlight", List.of((String) SPECIES_DATA[index][2]));

        // Temperature ranges (°C) — vary by index
        double minTemp = 10.0 + (index % 6) * 2.5;
        double maxTemp = 30.0 + (index % 5) * 2.0;
        env.put("minTempC", minTemp);
        env.put("maxTempC", maxTemp);

        env.put("imageUrl", "https://placeholder.leafy.io/species/" + PERENUAL_IDS[index % PERENUAL_IDS.length] + ".jpg");

        // Some species have aliases, some don't
        if (index % 3 != 2) {
            env.put("aliases", List.of(
                    "Local-" + SPECIES_DATA[index][0],
                    "VN-" + SPECIES_DATA[index][0]));
        }

        // Humidity range for variety
        env.put("humidityMin", 40 + (index % 4) * 10);
        env.put("humidityMax", 70 + (index % 3) * 5);

        return env;
    }

    // -------------------------------------------------------------------------
    // Plant seeding
    // -------------------------------------------------------------------------

    private List<Plant> seedPlants(int count, List<String> speciesIds, List<String> farmPlotIds,
                                   Map<String, List<String>> zonesByPlot, Random random) {
        if (farmPlotIds.isEmpty()) {
            log.warn("No active farmPlots found. Planting {} plants without farmPlotId assignment.", count);
        }

        List<Plant> plants = new ArrayList<>(count);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            PlantStatus status = plantStatusForIndex(i);
            String sourceType = SOURCE_TYPES[i % SOURCE_TYPES.length];
            String speciesId = speciesIds.isEmpty() ? null : speciesIds.get(i % speciesIds.size());
            String farmPlotId = farmPlotIds.isEmpty() ? null : farmPlotIds.get(i % farmPlotIds.size());

            // 70% of plants get a zone assignment
            String farmZoneId = null;
            if (farmPlotId != null && i % 10 < 7) {
                List<String> zones = zonesByPlot.get(farmPlotId);
                if (zones != null && !zones.isEmpty()) {
                    farmZoneId = zones.get(i % zones.size());
                }
            }

            LocalDateTime plantingDate = now.minusDays(180 + (long) (i * 7));
            LocalDateTime germinationDate = (i % 2 == 0) ? plantingDate.plusDays(7 + (i % 10)) : null;
            LocalDateTime actualHarvestDate = null;
            Double totalYieldKg = null;

            if (status == PlantStatus.ARCHIVED) {
                actualHarvestDate = plantingDate.plusDays(90 + (long) (i % 30) * 3);
                totalYieldKg = 0.5 + (i % 20) * 0.25;
            }

            Plant plant = Plant.builder()
                    .plantNumber(String.format("PLT-%06d", i + 1))
                    .plantStatus(status)
                    .nickName(buildPlantNickname(i))
                    .tagCode(String.format("TAG-%04d", i + 1))
                    .batchNumber(buildBatchNumber(i, now))
                    .sourceType(sourceType)
                    .motherPlantId(null)        // filled in second pass below
                    .plantingDate(plantingDate)
                    .germinationDate(germinationDate)
                    .actualHarvestDate(actualHarvestDate)
                    .totalYieldKg(totalYieldKg)
                    .speciesId(speciesId)
                    .farmPlotId(farmPlotId)
                    .farmZoneId(farmZoneId)
                    .build();

            plant.setActive(status != PlantStatus.ARCHIVED);
            plants.add(plant);
        }

        // Save first pass (without motherPlantId)
        List<Plant> saved = plantRepository.saveAll(plants);

        // Second pass: assign motherPlantId to ~30% of plants (index % 3 == 0, skip first 3)
        List<Plant> toUpdate = new ArrayList<>();
        for (int i = 3; i < saved.size(); i++) {
            if (i % 3 == 0) {
                Plant plant = saved.get(i);
                plant.setMotherPlantId(saved.get(i % 3).getId());
                toUpdate.add(plant);
            }
        }
        if (!toUpdate.isEmpty()) {
            plantRepository.saveAll(toUpdate);
        }

        // Return the full saved list (with updated references merged in)
        saved.forEach(p -> toUpdate.stream()
                .filter(u -> u.getId().equals(p.getId()))
                .findFirst()
                .ifPresent(u -> p.setMotherPlantId(u.getMotherPlantId())));
        return saved;
    }

    private PlantStatus plantStatusForIndex(int index) {
        int mod10 = index % 10;
        if (mod10 < 6) return PlantStatus.ACTIVE;
        if (mod10 < 8) return PlantStatus.INACTIVE;
        return PlantStatus.ARCHIVED;
    }

    private String buildPlantNickname(int index) {
        String[] adjectives = {"Robust", "Hardy", "Vigorous", "Lush", "Thriving", "Healthy", "Giant", "Dwarf", "Wild", "Rare"};
        String[] nouns = {"Star", "Gem", "Crown", "Pride", "Sprout", "Bloom", "Vine", "Bush", "Tree", "Leaf"};
        return adjectives[index % adjectives.length] + " " + nouns[(index / adjectives.length) % nouns.length];
    }

    private String buildBatchNumber(int index, LocalDateTime now) {
        String[] seasons = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};
        String season = seasons[(now.getMonthValue() - 1) / 3];
        return "BATCH-" + season + "-" + (now.getYear() - (index % 2));
    }

    // -------------------------------------------------------------------------
    // PlantEvent seeding
    // -------------------------------------------------------------------------

    private List<PlantEvent> seedEvents(List<Plant> plants, List<String> farmPlotIds,
                                        Map<String, List<String>> zonesByPlot,
                                        int eventsPerPlant, Random random) {
        List<PlantEvent> events = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Guarantee all 12 EventType values are covered: first 12 plants each cover one type
        for (int i = 0; i < Math.min(plants.size(), ALL_EVENT_TYPES.length); i++) {
            Plant plant = plants.get(i);
            EventType forcedType = ALL_EVENT_TYPES[i];
            events.add(buildEvent(plant, forcedType, i, today, random));
        }

        // Remaining events per plant (random types)
        for (int i = 0; i < plants.size(); i++) {
            Plant plant = plants.get(i);
            int extraEvents = (i < ALL_EVENT_TYPES.length) ? eventsPerPlant - 1 : eventsPerPlant;
            for (int e = 0; e < extraEvents; e++) {
                EventType type = ALL_EVENT_TYPES[(i * eventsPerPlant + e) % ALL_EVENT_TYPES.length];
                events.add(buildEvent(plant, type, i * eventsPerPlant + e + ALL_EVENT_TYPES.length, today, random));
            }
        }

        return plantEventRepository.saveAll(events);
    }

    private PlantEvent buildEvent(Plant plant, EventType eventType, int seqIndex,
                                  LocalDate today, Random random) {
        // Alternate planned (future) / actual (past)
        boolean planned = seqIndex % 2 == 0;
        int dayOffset = planned ? (5 + seqIndex % 30) : -(1 + seqIndex % 60);
        int durationDays = 1 + (seqIndex % 7);

        LocalDate calcStart = today.plusDays(dayOffset);
        LocalDate calcEnd = calcStart.plusDays(durationDays);

        String[] noteOptions = EVENT_NOTES.getOrDefault(eventType, new String[]{"Seeded event for " + eventType});
        String note = noteOptions[seqIndex % noteOptions.length];
        String description = "Auto-seeded " + eventType.name().toLowerCase().replace('_', ' ')
                + " event #" + (seqIndex + 1);

        // ~20% of events have a sourcePlanId (simulating RAG-generated plans)
        String sourcePlanId = (seqIndex % 5 == 0)
                ? "PLAN-SEED-" + String.format("%04d", seqIndex + 1)
                : null;

        PlantEvent.PlantEventBuilder builder = PlantEvent.builder()
                .plantId(plant.getId())
                .farmPlotId(plant.getFarmPlotId())
                .farmZoneId(plant.getFarmZoneId())
                .eventType(eventType)
                .note(note)
                .description(description)
                .daysFromNow(dayOffset)
                .durationDays(durationDays)
                .planned(planned)
                .calculatedStartDate(calcStart)
                .calculatedEndDate(calcEnd)
                .sourcePlanId(sourcePlanId);

        // Chemical safety fields — only for TREATMENT_APPLICATION
        if (eventType == EventType.TREATMENT_APPLICATION) {
            int[] phiOptions = {7, 14, 21};
            String[] ppeOptions = {"Gloves and goggles", "Full protective suit", "Gloves and mask"};
            String[] mrlOptions = {"Within EU MRL 0.05 mg/kg", "Observe 14-day PHI before harvest", "MRL compliant per CODEX"};
            String[] costOptions = {"125.00", "250.50", "80.00", "310.00"};

            builder.phiDays(phiOptions[seqIndex % phiOptions.length])
                   .ppeRequired(ppeOptions[seqIndex % ppeOptions.length])
                   .mrlNote(mrlOptions[seqIndex % mrlOptions.length])
                   .estimatedCost(costOptions[seqIndex % costOptions.length]);
        }

        PlantEvent event = builder.build();
        event.setActive(true);
        return event;
    }

    // -------------------------------------------------------------------------
    // Farm data fetching via Feign
    // -------------------------------------------------------------------------

    private List<FarmPlotSummary> fetchFarmPlots(UserPrincipal currentUser, String roleHeader) {
        try {
            ExternalApiResponse<List<FarmPlotSummary>> response = farmServiceClient.getAllActiveFarmPlots(
                    currentUser.getUserId(),
                    currentUser.getEmail(),
                    roleHeader,
                    currentUser.getProfileId());

            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .filter(p -> p.getId() != null && !p.getId().isBlank())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not fetch farm plots from farm-service: {}. Plants will be seeded without farmPlotId.", e.getMessage());
        }
        return List.of();
    }

    private List<FarmZoneSummary> fetchFarmZones(UserPrincipal currentUser, String roleHeader) {
        try {
            ExternalApiResponse<List<FarmZoneSummary>> response = farmServiceClient.getAllActiveFarmZones(
                    currentUser.getUserId(),
                    currentUser.getEmail(),
                    roleHeader,
                    currentUser.getProfileId());

            if (response != null && response.getData() != null) {
                return response.getData().stream()
                        .filter(z -> z.getId() != null && !z.getId().isBlank())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Could not fetch farm zones from farm-service: {}. Plants will be seeded without farmZoneId.", e.getMessage());
        }
        return List.of();
    }
}
