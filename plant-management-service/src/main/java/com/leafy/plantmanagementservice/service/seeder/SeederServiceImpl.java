package com.leafy.plantmanagementservice.service.seeder;

import com.leafy.plantmanagementservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.plantmanagementservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.plantmanagementservice.service.farmplot.FarmPlotService;
import com.leafy.plantmanagementservice.service.farmzone.FarmZoneService;
import com.leafy.plantmanagementservice.config.SeederProperties;
import com.leafy.plantmanagementservice.dto.response.seeder.PlantSeederResponse;
import com.leafy.plantmanagementservice.model.EmbeddedPlanEvent;
import com.leafy.plantmanagementservice.model.EventTask;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.Species;
import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.model.PlanApply;
import com.leafy.plantmanagementservice.model.enums.EventType;
import com.leafy.plantmanagementservice.model.enums.PlanSourceType;
import com.leafy.plantmanagementservice.model.enums.PlantStatus;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.model.enums.TargetType;
import com.leafy.plantmanagementservice.model.enums.TrackingGranularity;
import com.leafy.plantmanagementservice.model.enums.SeverityLevel;
import com.leafy.plantmanagementservice.repository.EventProgressRepository;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import com.leafy.plantmanagementservice.repository.PlantRepository;
import com.leafy.plantmanagementservice.repository.SpeciesRepository;
import com.leafy.plantmanagementservice.repository.PlanApplyRepository;
import com.leafy.plantmanagementservice.repository.PlanRepository;
import com.leafy.plantmanagementservice.service.eventprogress.EventProgressService;
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
    EventProgressRepository eventProgressRepository;
    EventProgressService eventProgressService;
    PlanRepository planRepository;
    PlanApplyRepository planApplyRepository;
    FarmPlotService farmPlotService;
    FarmZoneService farmZoneService;
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

    // Per event-type sub-tasks: [title, description, estimatedCost|null]
    private static final Map<EventType, String[][]> EVENT_TASKS = new HashMap<>() {{
        put(EventType.IRRIGATION, new String[][]{
            {"Kiểm tra độ ẩm đất", "Đo độ ẩm tại 3 điểm xung quanh gốc cây trước khi tưới", null},
            {"Mở van tưới nhỏ giọt", "Bật hệ thống tưới nhỏ giọt, kiểm tra áp suất đường ống", null},
            {"Ghi nhận lượng nước", "Ghi chép lượng nước sử dụng vào nhật ký chăm sóc", null},
        });
        put(EventType.NUTRITION, new String[][]{
            {"Pha dung dịch phân bón", "Pha NPK theo tỷ lệ 50g/10L nước", "15,000 VND"},
            {"Bón phân quanh gốc", "Rải đều phân quanh tán cây, tránh tiếp xúc thân", null},
            {"Tưới nước sau bón phân", "Tưới đẫm sau khi bón để hoà tan phân vào đất", null},
        });
        put(EventType.WEED_CONTROL, new String[][]{
            {"Nhổ cỏ dại quanh gốc", "Nhổ tay toàn bộ cỏ trong bán kính 30cm quanh gốc", null},
            {"Xới đất giữa các hàng", "Dùng cuốc xới nhẹ đất giữa các hàng cây", null},
        });
        put(EventType.PRUNING, new String[][]{
            {"Vệ sinh dụng cụ cắt tỉa", "Lau cồn 70° lên kéo/dao trước khi sử dụng", "5,000 VND"},
            {"Cắt bỏ cành chết/bệnh", "Loại bỏ cành khô, cành bị bệnh xuống sát thân chính", null},
            {"Xử lý vết cắt", "Phủ thuốc bảo vệ vết cắt để ngăn nhiễm khuẩn", "10,000 VND"},
        });
        put(EventType.SCOUTING, new String[][]{
            {"Kiểm tra mặt dưới lá", "Quan sát kỹ mặt dưới lá để phát hiện sâu, trứng, nấm", null},
            {"Ghi nhận tình trạng cây", "Chụp ảnh và ghi chép tình trạng tổng quát của cây", null},
        });
        put(EventType.DISEASE_DETECTED, new String[][]{
            {"Cô lập cây bị bệnh", "Đặt rào cản và biển báo quanh cây bị nhiễm bệnh", null},
            {"Lấy mẫu lá bệnh", "Thu thập 3–5 lá bệnh điển hình để giám định", null},
            {"Thông báo kỹ thuật viên", "Liên hệ kỹ thuật viên xác định loại bệnh và phương án xử lý", null},
        });
        put(EventType.TREATMENT_APPLICATION, new String[][]{
            {"Chuẩn bị bảo hộ lao động", "Mặc đồ bảo hộ đầy đủ: găng tay, khẩu trang, kính bảo hộ", "30,000 VND"},
            {"Pha thuốc đúng liều lượng", "Pha theo hướng dẫn trên nhãn, đúng nồng độ quy định", "80,000 VND"},
            {"Phun thuốc và vệ sinh sau phun", "Phun đều hai mặt lá, rửa sạch bình và tay sau khi hoàn thành", "10,000 VND"},
        });
        put(EventType.QUARANTINE, new String[][]{
            {"Rào cách ly khu vực", "Dựng hàng rào tạm và treo biển cảnh báo kiểm dịch", null},
            {"Khử trùng dụng cụ", "Ngâm toàn bộ dụng cụ đã tiếp xúc vào dung dịch khử trùng", "20,000 VND"},
        });
        put(EventType.HEALTH_RECOVERY, new String[][]{
            {"Theo dõi sự xuất hiện chồi mới", "Quan sát và ghi lại chồi/lá mới sau xử lý", null},
            {"Bổ sung dinh dưỡng phục hồi", "Bón phân vi lượng giúp cây phục hồi nhanh hơn", "25,000 VND"},
        });
        put(EventType.PHENOLOGY, new String[][]{
            {"Ghi nhận giai đoạn sinh trưởng", "Chụp ảnh và ghi chú giai đoạn phát triển hiện tại", null},
            {"Đếm nụ/hoa/quả", "Đếm số lượng nụ, hoa hoặc quả non hiện có trên cây", null},
        });
        put(EventType.REPOT, new String[][]{
            {"Chuẩn bị giá thể mới", "Trộn đất vườn, phân hữu cơ và perlite theo tỷ lệ 2:1:1", "40,000 VND"},
            {"Bứng cây và cắt rễ hỏng", "Nhẹ nhàng tháo bầu đất cũ, loại bỏ rễ thối", null},
            {"Trồng lại và tưới ổn định", "Đặt cây vào chậu mới, lấp đất và tưới đẫm", null},
        });
        put(EventType.HARVEST, new String[][]{
            {"Kiểm tra độ chín của quả", "Xác nhận màu sắc và độ cứng đạt tiêu chuẩn thu hoạch", null},
            {"Thu hái và phân loại", "Hái quả chín, phân loại theo kích cỡ và chất lượng", null},
            {"Cân và ghi nhật ký sản lượng", "Ghi lại sản lượng thu hoạch vào sổ theo dõi", null},
        });
    }};

    @Override
    @Transactional
    public PlantSeederResponse reseed(Integer speciesCount, Integer plantCount, Integer eventsPerPlant, Integer planCount) {
        int effectiveSpeciesCount = speciesCount != null ? speciesCount : seederProperties.getSpeciesCount();
        int effectivePlantCount = plantCount != null ? plantCount : seederProperties.getPlantCount();
        int effectiveEventsPerPlant = eventsPerPlant != null ? eventsPerPlant : seederProperties.getEventsPerPlant();
        int effectivePlanCount = planCount != null ? planCount : seederProperties.getPlanCount();

        // Fetch farm data for referential integrity
        List<FarmPlotResponse> farmPlots = fetchFarmPlots();
        List<FarmZoneResponse> farmZones = fetchFarmZones();

        log.info("Plant seeder: {} farmPlots, {} farmZones available. Seeding {} species, {} plants, {} events/plant, {} plans",
                farmPlots.size(), farmZones.size(), effectiveSpeciesCount, effectivePlantCount, effectiveEventsPerPlant, effectivePlanCount);

        // Build farmPlotId -> List<zoneId> lookup
        Map<String, List<String>> zonesByPlot = farmZones.stream()
                .filter(z -> z.getFarmPlotId() != null)
                .collect(Collectors.groupingBy(
                        FarmZoneResponse::getFarmPlotId,
                        Collectors.mapping(FarmZoneResponse::getId, Collectors.toList())));

        // Collect plot IDs that have at least one zone (for varied zone assignment)
        List<String> farmPlotIds = farmPlots.stream().map(FarmPlotResponse::getId).toList();

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
        long deletedPlanCount = planRepository.count();
        long deletedPlanApplyCount = planApplyRepository.count();
        long deletedProgressCount = eventProgressRepository.count();
        eventProgressRepository.deleteAll();    // progress must go first
        plantEventRepository.deleteAll();       // events must go first
        planApplyRepository.deleteAll();        // applies must go before plans
        planRepository.deleteAll();
        plantRepository.deleteAll();

        Map<String, List<FarmPlotResponse>> plotsByOwner = farmPlots.stream()
                .filter(p -> p.getOwnerProfileId() != null)
                .collect(Collectors.groupingBy(FarmPlotResponse::getOwnerProfileId));

        List<Plant> allSeededPlants = new ArrayList<>();
        List<PlantEvent> allSeededEvents = new ArrayList<>();
        List<PlantEvent> allFarmScopedEvents = new ArrayList<>();

        int userIndex = 0;
        int eventSeqIndex = 0;

        if (plotsByOwner.isEmpty()) {
            log.warn("No active farmPlots found with owner profiles. Planting globally without farmPlotId assignment.");
            List<String> emptyPlots = List.of();
            List<Plant> seededPlants = seedPlants(
                    effectivePlantCount, speciesIds, emptyPlots, zonesByPlot, random, 0, null);
            List<PlantEvent> seededEvents = seedEvents(seededPlants, emptyPlots, zonesByPlot,
                    effectiveEventsPerPlant, random, 0);
            allSeededPlants.addAll(seededPlants);
            allSeededEvents.addAll(seededEvents);
        } else {
            for (Map.Entry<String, List<FarmPlotResponse>> entry : plotsByOwner.entrySet()) {
                String ownerProfileId = entry.getKey();
                List<String> userPlotIds = entry.getValue().stream().map(FarmPlotResponse::getId).toList();

                List<Plant> seededPlants = seedPlants(
                        effectivePlantCount, speciesIds, userPlotIds, zonesByPlot, random,
                        userIndex * effectivePlantCount, ownerProfileId);

                List<PlantEvent> seededEvents = seedEvents(seededPlants, userPlotIds, zonesByPlot,
                        effectiveEventsPerPlant, random, eventSeqIndex);

                eventSeqIndex += seededEvents.size();

                // Farm-scoped events with tracking granularity
                List<PlantEvent> farmScopedEvents = seedFarmScopedEvents(
                        userPlotIds, zonesByPlot, ownerProfileId, eventSeqIndex);
                eventSeqIndex += farmScopedEvents.size();

                allSeededPlants.addAll(seededPlants);
                allSeededEvents.addAll(seededEvents);
                allFarmScopedEvents.addAll(farmScopedEvents);
                userIndex++;
            }
        }

        // --- Plans + PlanApplies (seeded per-plant from freshly seeded events) ---
        List<Plan> allSeededPlans = seedPlansFromEvents(allSeededPlants, allSeededEvents, effectivePlanCount);

        // --- Generate EventProgress for all farm-scoped tracked events ---
        int totalProgressEntries = 0;
        for (PlantEvent event : allFarmScopedEvents) {
            if (event.getTrackingGranularity() != null
                    && event.getTrackingGranularity() != TrackingGranularity.NONE) {
                totalProgressEntries += eventProgressService.generateForEvent(event).size();
            }
        }

        long seededPlanApplyCount = planApplyRepository.count();
        int totalEventCount = allSeededEvents.size() + allFarmScopedEvents.size();
        log.info("Plant seeder complete: species={}, plants={}, events={} ({}+{} farm-scoped), progress={}, plans={}, applies={}",
                speciesIds.size(), allSeededPlants.size(), totalEventCount,
                allSeededEvents.size(), allFarmScopedEvents.size(),
                totalProgressEntries, allSeededPlans.size(), seededPlanApplyCount);

        return PlantSeederResponse.builder()
                .seededSpeciesCount(speciesCounts[0])
                .createdSpeciesCount(speciesCounts[1])
                .updatedSpeciesCount(speciesCounts[2])
                .deletedPlantCount(deletedPlantCount)
                .seededPlantCount(allSeededPlants.size())
                .deletedEventCount(deletedEventCount)
                .seededEventCount(totalEventCount)
                .deletedProgressCount(deletedProgressCount)
                .seededProgressCount(totalProgressEntries)
                .deletedPlanCount(deletedPlanCount)
                .seededPlanCount(allSeededPlans.size())
                .deletedPlanApplyCount(deletedPlanApplyCount)
                .seededPlanApplyCount((int) seededPlanApplyCount)
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
                                   Map<String, List<String>> zonesByPlot, Random random, int startIndex,
                                   String ownerProfileId) {
        if (farmPlotIds.isEmpty()) {
            log.warn("No active farmPlots found. Planting {} plants without farmPlotId assignment.", count);
        }

        List<Plant> plants = new ArrayList<>(count);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < count; i++) {
            int globalIndex = startIndex + i;
            PlantStatus status = plantStatusForIndex(globalIndex);
            String sourceType = SOURCE_TYPES[globalIndex % SOURCE_TYPES.length];
            String speciesId = speciesIds.isEmpty() ? null : speciesIds.get(globalIndex % speciesIds.size());
            String farmPlotId = farmPlotIds.isEmpty() ? null : farmPlotIds.get(i % farmPlotIds.size());

            // 70% of plants get a zone assignment
            String farmZoneId = null;
            if (farmPlotId != null && globalIndex % 10 < 7) {
                List<String> zones = zonesByPlot.get(farmPlotId);
                if (zones != null && !zones.isEmpty()) {
                    farmZoneId = zones.get(i % zones.size());
                }
            }

            LocalDateTime plantingDate = now.minusDays(180 + (long) (globalIndex * 7));
            LocalDateTime germinationDate = (globalIndex % 2 == 0) ? plantingDate.plusDays(7 + (globalIndex % 10)) : null;
            LocalDateTime actualHarvestDate = null;
            Double totalYieldKg = null;

            if (status == PlantStatus.ARCHIVED) {
                actualHarvestDate = plantingDate.plusDays(90 + (long) (globalIndex % 30) * 3);
                totalYieldKg = 0.5 + (globalIndex % 20) * 0.25;
            }

            Plant plant = Plant.builder()
                    .plantNumber(String.format("PLT-%06d", globalIndex + 1))
                    .plantStatus(status)
                    .nickName(buildPlantNickname(globalIndex))
                    .tagCode(String.format("TAG-%04d", globalIndex + 1))
                    .batchNumber(buildBatchNumber(globalIndex, now))
                    .motherPlantId(null)        // filled in second pass below
                    .plantingDate(plantingDate)
                    .germinationDate(germinationDate)
                    .actualHarvestDate(actualHarvestDate)
                    .totalYieldKg(totalYieldKg)
                    .speciesId(speciesId)
                    .farmPlotId(farmPlotId)
                    .farmZoneId(farmZoneId)
                    .ownerProfileId(ownerProfileId)
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
                                        int eventsPerPlant, Random random, int startSeqIndex) {
        List<PlantEvent> events = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int currentSeq = startSeqIndex;

        // Guarantee all 12 EventType values are covered: first 12 plants each cover one type
        for (int i = 0; i < Math.min(plants.size(), ALL_EVENT_TYPES.length); i++) {
            Plant plant = plants.get(i);
            EventType forcedType = ALL_EVENT_TYPES[i];
            events.add(buildEvent(plant, forcedType, currentSeq++, today, random));
        }

        // Remaining events per plant (random types)
        for (int i = 0; i < plants.size(); i++) {
            Plant plant = plants.get(i);
            int extraEvents = (i < ALL_EVENT_TYPES.length) ? eventsPerPlant - 1 : eventsPerPlant;
            for (int e = 0; e < extraEvents; e++) {
                EventType type = ALL_EVENT_TYPES[currentSeq % ALL_EVENT_TYPES.length];
                events.add(buildEvent(plant, type, currentSeq++, today, random));
            }
        }

        return plantEventRepository.saveAll(events);
    }

    private PlantEvent buildEvent(Plant plant, EventType eventType, int seqIndex,
                                  LocalDate today, Random random) {
        // Alternate planned (future) / actual (past)
        boolean planned = seqIndex % 2 == 0;
        int calcOffset = planned ? (5 + seqIndex % 30) : -(1 + seqIndex % 60);
        int durationDays = 1 + (seqIndex % 7);
        int daysFromStartVal = seqIndex % 30; // Enforce non-negative daysFromStart

        LocalDate calcStart = today.plusDays(calcOffset);
        LocalDate calcEnd = calcStart.plusDays(durationDays);

        // Completed: only past events can be completed (~70% chance for past events)
        boolean completed = !planned && (seqIndex % 10 < 7);

        String[] noteOptions = EVENT_NOTES.getOrDefault(eventType, new String[]{"Seeded event for " + eventType});
        String note = noteOptions[seqIndex % noteOptions.length];
        String description = "Auto-seeded " + eventType.name().toLowerCase().replace('_', ' ')
                + " event #" + (seqIndex + 1);

        PlantEvent.PlantEventBuilder builder = PlantEvent.builder()
                .plantId(plant.getId())
                .farmPlotId(plant.getFarmPlotId())
                .farmZoneId(plant.getFarmZoneId())
                .eventType(eventType)
                .targetType(TargetType.PLANT)   // plant-scoped events always target an individual plant
                .note(note)
                .description(description)
                .daysFromStart(daysFromStartVal)
                .durationDays(durationDays)
                .planned(planned)
                .completed(completed)
                .calculatedStartDate(calcStart)
                .calculatedEndDate(calcEnd);

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

        // Add tasks to ~67% of events (seqIndex % 3 != 0)
        if (seqIndex % 3 != 0) {
            String[][] taskTemplates = EVENT_TASKS.getOrDefault(eventType, new String[][]{
                {"Thực hiện công việc", "Hoàn thành nhiệm vụ theo kế hoạch", null},
            });
            int taskCount = Math.min(1 + (seqIndex % taskTemplates.length), taskTemplates.length);
            List<EventTask> tasks = new ArrayList<>(taskCount);
            for (int t = 0; t < taskCount; t++) {
                String[] tmpl = taskTemplates[(seqIndex + t) % taskTemplates.length];
                boolean taskDone;
                if (completed) {
                    taskDone = true;  // all tasks done when event is completed
                } else if (!planned) {
                    taskDone = t < taskCount - 1 && seqIndex % 2 == 0;  // earlier tasks done for in-progress
                } else {
                    taskDone = false; // future planned events: nothing done yet
                }
                tasks.add(EventTask.builder()
                        .title(tmpl[0])
                        .description(tmpl[1])
                        .estimatedCost(tmpl[2])
                        .order(t)
                        .completed(taskDone)
                        .build());
            }
            builder.tasks(tasks);
        }

        PlantEvent event = builder.build();
        event.setActive(true);
        return event;
    }

    // -------------------------------------------------------------------------
    // Farm-scoped PlantEvent seeding (with progress tracking)
    // -------------------------------------------------------------------------

    /**
     * For each farm plot, seeds:
     * <ol>
     *   <li>One plot-scoped event with {@link TrackingGranularity#ZONE}</li>
     *   <li>One plot-scoped event with {@link TrackingGranularity#PLANT}</li>
     *   <li>One zone-scoped event with {@link TrackingGranularity#PLANT} per first zone of the plot</li>
     * </ol>
     * Events are saved and returned; progress generation is handled by the caller via
     * {@link com.leafy.plantmanagementservice.service.eventprogress.EventProgressService#generateForEvent}.
     */
    private List<PlantEvent> seedFarmScopedEvents(List<String> farmPlotIds,
                                                   Map<String, List<String>> zonesByPlot,
                                                   String ownerProfileId, int startSeqIndex) {
        if (farmPlotIds.isEmpty()) {
            return List.of();
        }

        List<PlantEvent> events = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int seq = startSeqIndex;

        EventType[] farmEventTypes = {
            EventType.IRRIGATION, EventType.SCOUTING, EventType.NUTRITION,
            EventType.WEED_CONTROL, EventType.PRUNING
        };

        for (String farmPlotId : farmPlotIds) {
            // 1. Plot-scoped event tracked by zone
            events.add(buildFarmScopedEvent(farmPlotId, null,
                    farmEventTypes[seq % farmEventTypes.length],
                    TrackingGranularity.ZONE, seq++, today));

            // 2. Plot-scoped event tracked by plant
            events.add(buildFarmScopedEvent(farmPlotId, null,
                    farmEventTypes[seq % farmEventTypes.length],
                    TrackingGranularity.PLANT, seq++, today));

            // 3. One zone-scoped event tracked by plant per first zone of the plot
            List<String> zones = zonesByPlot.getOrDefault(farmPlotId, List.of());
            if (!zones.isEmpty()) {
                events.add(buildFarmScopedEvent(farmPlotId, zones.get(0),
                        farmEventTypes[seq % farmEventTypes.length],
                        TrackingGranularity.PLANT, seq++, today));
            }
        }

        return plantEventRepository.saveAll(events);
    }

    private PlantEvent buildFarmScopedEvent(String farmPlotId, String farmZoneId,
                                             EventType eventType, TrackingGranularity granularity,
                                             int seqIndex, LocalDate today) {
        boolean planned = seqIndex % 2 == 0;
        int calcOffset = planned ? (5 + seqIndex % 30) : -(1 + seqIndex % 60);
        int durationDays = 1 + (seqIndex % 7);
        int daysFromStartVal = seqIndex % 30; // Enforce non-negative daysFromStart

        LocalDate calcStart = today.plusDays(calcOffset);
        LocalDate calcEnd = calcStart.plusDays(durationDays);

        String[] noteOptions = EVENT_NOTES.getOrDefault(eventType, new String[]{"Seeded event for " + eventType});
        String note = noteOptions[seqIndex % noteOptions.length];
        String scope = farmZoneId != null ? "zone" : "farm-plot";
        String description = "Auto-seeded " + scope + "-level "
                + eventType.name().toLowerCase().replace('_', ' ')
                + " event #" + (seqIndex + 1) + " [tracking=" + granularity.name() + "]";

        // Derive scope: zone-scoped if farmZoneId is present, otherwise farm-plot-scoped
        TargetType targetType = farmZoneId != null ? TargetType.FARM_ZONE : TargetType.FARM;

        PlantEvent event = PlantEvent.builder()
                .farmPlotId(farmPlotId)
                .farmZoneId(farmZoneId)
                .eventType(eventType)
                .targetType(targetType)
                .note(note)
                .description(description)
                .daysFromStart(daysFromStartVal)
                .durationDays(durationDays)
                .planned(planned)
                .completed(false)
                .calculatedStartDate(calcStart)
                .calculatedEndDate(calcEnd)
                .trackingGranularity(granularity)
                .progressTotal(0)
                .progressCompleted(0)
                .build();
        event.setActive(true);
        return event;
    }

    private List<Plan> seedPlansFromEvents(List<Plant> plants, List<PlantEvent> events, int planCount) {
        // Build plantId -> ownerProfileId from freshly-seeded plants
        Map<String, String> ownerByPlant = plants.stream()
                .filter(p -> p.getOwnerProfileId() != null)
                .collect(Collectors.toMap(Plant::getId, Plant::getOwnerProfileId, (a, b) -> a));

        // Group events by plantId; one plan per plant up to planCount
        Map<String, List<PlantEvent>> eventsByPlant = events.stream()
                .filter(e -> e.getPlantId() != null)
                .collect(Collectors.groupingBy(PlantEvent::getPlantId));

        List<String> plantIds = new ArrayList<>(eventsByPlant.keySet());
        int limit = Math.min(planCount, plantIds.size());

        // ── Disease / plan data for richer seeding ──────────────────────────
        String[][] diseaseData = {
            {"Bệnh gỉ sắt (Leaf Rust)",          "HIGH",   "IMMEDIATE", "Thuốc trị nấm đồng (Copper fungicide)", "Vết bệnh cũ khô lại, lá non không có đốm vàng"},
            {"Bệnh khô cành (Twig Blight)",       "MEDIUM", "HIGH",      "Thuốc Mancozeb, kéo cắt tỉa",          "Cành mới ra không có triệu chứng khô"},
            {"Bệnh phấn trắng (Powdery Mildew)",  "LOW",    "NORMAL",    "Lưu huỳnh dạng bột (Sulfur dust)",      "Lớp bột trắng biến mất sau 7 ngày"},
            {"Bệnh thán thư (Anthracnose)",        "HIGH",   "IMMEDIATE", "Thuốc Propineb 70WP, bình phun 16L",    "Quả non không còn vết đốm nâu"},
            {"Rệp sáp (Mealybug Infestation)",    "MEDIUM", "HIGH",      "Dầu neem, bông gòn, xà phòng loãng",   "Không còn thấy rệp sáp trên thân và lá"},
            {"Bệnh vàng lá (Chlorosis)",           "LOW",    "NORMAL",    "Phân vi lượng sắt (Fe-EDTA)",           "Lá chuyển xanh trở lại sau 2-3 tuần"},
        };

        List<Plan> plans = new ArrayList<>();
        List<PlanApply> planApplies = new ArrayList<>();
        List<PlantEvent> eventsToUpdate = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            String plantId = plantIds.get(i);
            List<PlantEvent> plantEvents = eventsByPlant.get(plantId);

            String planId = String.format("5eed0000000000000000%04x", i + 1);

            // Distribute apply statuses: 33% COMPLETED, 34% PENDING (no apply), 33% ACTIVE
            PlanStatus applyStatus;
            if (i % 3 == 0) applyStatus = PlanStatus.COMPLETED;
            else if (i % 3 == 1) applyStatus = null; // No apply — plan is just a template
            else applyStatus = PlanStatus.ACTIVE;

            // ~40% of plans are public (visible in community feed)
            boolean isPublic = (i % 5 < 2);

            PlantEvent firstEvent = plantEvents.get(0);
            String ownerProfileId = ownerByPlant.get(plantId);

            // Pick disease data cyclically
            String[] disease = diseaseData[i % diseaseData.length];
            String diseaseName    = disease[0];
            String severityLevel  = disease[1];
            String urgency        = disease[2];
            String requiredInput  = disease[3];
            String successInd     = disease[4];
            String planName       = "Kế hoạch " + diseaseName.split("\\(")[0].trim() + " - " + (i + 1);

            String applyId = new org.bson.types.ObjectId().toString();

            // Link up to 3 events per plan as template events
            List<PlantEvent> linkedEvents = plantEvents.subList(0, Math.min(3, plantEvents.size()));
            linkedEvents.forEach(e -> {
                if (applyStatus != null) {
                    e.setPlanApplyId(applyId);
                    eventsToUpdate.add(e);
                }
            });

            // Build EmbeddedPlanEvent list from the linked PlantEvents (up to 3)
            List<EmbeddedPlanEvent> embeddedEvents = linkedEvents.stream()
                    .map(e -> EmbeddedPlanEvent.builder()
                            .eventType(e.getEventType())
                            .targetType(e.getTargetType())   // carry scope from the source PlantEvent
                            .note(e.getNote())
                            .description(e.getDescription())
                            .daysFromStart(e.getDaysFromStart())
                            .durationDays(e.getDurationDays())
                            .phiDays(e.getPhiDays())
                            .ppeRequired(e.getPpeRequired())
                            .mrlNote(e.getMrlNote())
                            .estimatedCost(e.getEstimatedCost())
                            .tasks(e.getTasks())
                            .build())
                    .collect(java.util.stream.Collectors.toList());

            PlanSourceType[] types = PlanSourceType.values();
            PlanSourceType sourceType = types[i % types.length];
            
            String ragId = sourceType == PlanSourceType.RAG_GEN ? "rag-" + planId : null;
            String src = sourceType == PlanSourceType.RAG_GEN ? "RAG-SEEDER" : null;

            Plan plan = Plan.builder()
                    .id(planId)
                    .creatorId(ownerProfileId)
                    .ownerId(ownerProfileId)
                    .sourceType(sourceType)
                    .planName(planName)
                    .source(src)
                    .diseaseName(diseaseName)
                    .confidenceScore(0.75 + (i % 10) * 0.02)
                    .severityLevel(SeverityLevel.valueOf(severityLevel.toUpperCase()))
                    .requiredInputs(List.of(requiredInput, "Bình phun 16L", "Bảo hộ lao động (găng tay, khẩu trang)"))
                    .safetyWarnings(List.of(
                        "Đeo đầy đủ bảo hộ lao động khi phun thuốc",
                        "Cách ly " + (7 + (i % 3) * 7) + " ngày trước khi thu hoạch"
                    ))
                    .successIndicators(successInd)
                    .estimatedCost((100 + (i % 8) * 50) + ",000 VND")
                    .events(embeddedEvents)
                    .isPublic(isPublic)
                    .build();

            plan.setActive(true);
            plans.add(plan);

            // Create a PlanApply for plans that have an apply status (non-PENDING)
            if (applyStatus != null) {
                PlanApply apply = PlanApply.builder()
                        .id(applyId)
                        .planId(planId)
                        .appliedById(ownerProfileId)
                        .plantId(plantId)
                        .farmPlotId(firstEvent.getFarmPlotId())
                        .farmZoneId(firstEvent.getFarmZoneId())
                        .startDate(LocalDate.now().minusDays(30 + i))
                        .plantEventIds(linkedEvents.stream().map(PlantEvent::getId).toList())
                        .status(applyStatus)
                        .build();
                apply.setActive(true);
                planApplies.add(apply);
            }
        }

        if (!eventsToUpdate.isEmpty()) {
            plantEventRepository.saveAll(eventsToUpdate);
        }

        long publicCount  = plans.stream().filter(Plan::isPublic).count();
        long privateCount = plans.stream().filter(p -> !p.isPublic()).count();
        log.info("Seeding {} plans ({} public, {} private) with {} applies",
                plans.size(), publicCount, privateCount, planApplies.size());

        List<Plan> savedPlans = planRepository.saveAll(plans);
        if (!planApplies.isEmpty()) {
            planApplyRepository.saveAll(planApplies);
        }
        return savedPlans;
    }


    // -------------------------------------------------------------------------
    // Farm data fetching via Feign
    // -------------------------------------------------------------------------

    private List<FarmPlotResponse> fetchFarmPlots() {
        try {
            return farmPlotService.getAllActive().stream()
                    .filter(p -> p.getId() != null && !p.getId().isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("Could not fetch farm plots: {}. Plants will be seeded without farmPlotId.", e.getMessage());
        }
        return List.of();
    }

    private List<FarmZoneResponse> fetchFarmZones() {
        try {
            return farmZoneService.getAllActive().stream()
                    .filter(z -> z.getId() != null && !z.getId().isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("Could not fetch farm zones: {}. Plants will be seeded without farmZoneId.", e.getMessage());
        }
        return List.of();
    }
}
