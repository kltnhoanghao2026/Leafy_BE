package com.leafy.plantmanagementservice.service.stats;

import com.leafy.common.utils.ServiceSecurityUtils;
import com.leafy.plantmanagementservice.dto.response.stats.AgricultureStatsResponse;
import com.leafy.plantmanagementservice.dto.response.stats.RecentEventSummary;
import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.model.Plant;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.repository.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level =  AccessLevel.PRIVATE, makeFinal = true)
public class AgricultureStatsServiceImpl implements AgricultureStatsService {

    FarmPlotRepository farmPlotRepository;
    FarmZoneRepository farmZoneRepository;
    PlantRepository plantRepository;
    PlantEventRepository plantEventRepository;
    PlanApplyRepository planApplyRepository;
    PlanRepository planRepository;

    @Override
    public AgricultureStatsResponse getStatsForCurrentUser() {
        String profileId = ServiceSecurityUtils.getCurrentProfileId();
        log.info("Computing agriculture stats for profileId={}", profileId);

        // ── 1. Farm data ──────────────────────────────────────────────────────
        List<FarmPlot> plots = farmPlotRepository.findByOwnerProfileIdAndActiveTrue(profileId);
        List<String> plotIds = plots.stream().map(FarmPlot::getId).toList();

        BigDecimal totalArea = plots.stream()
                .map(FarmPlot::getAreaM2)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FarmZone> zones = plotIds.isEmpty()
                ? Collections.emptyList()
                : farmZoneRepository.findByFarmPlotIdInAndActiveTrue(plotIds);
        List<String> zoneIds = zones.stream().map(FarmZone::getId).toList();

        // ── 2. Plant data ─────────────────────────────────────────────────────
        List<Plant> plants = plotIds.isEmpty()
                ? Collections.emptyList()
                : plantRepository.findByFarmPlotIdIn(plotIds);
        List<String> plantIds = plants.stream().map(Plant::getId).toList();

        int activePlants = 0, inactivePlants = 0, archivedPlants = 0;
        for (Plant p : plants) {
            if (p.getPlantStatus() == null) continue;
            switch (p.getPlantStatus()) {
                case ACTIVE -> activePlants++;
                case INACTIVE -> inactivePlants++;
                case ARCHIVED -> archivedPlants++;
            }
        }

        // ── 3. Event stats ────────────────────────────────────────────────────
        LocalDate today = LocalDate.now();
        LocalDate weekLater = today.plusDays(7);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());

        // Today's events
        long todayEvents = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, today, today, null, false);
        long todayCompleted = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, today, today, true, false);

        // This month events
        long monthEvents = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, monthStart, monthEnd, null, false);
        long monthCompleted = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, monthStart, monthEnd, true, false);
        long monthPending = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, monthStart, monthEnd, false, false);

        // Upcoming 7 days (excluding today)
        long upcoming7d = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, today.plusDays(1), weekLater, null, false);

        // Overdue
        long overdue = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, null, null, null, true);

        // Overall completion
        long totalCompleted = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, null, null, true, false);
        long totalPending = plantEventRepository.countProfileEventsFiltered(
                plotIds, zoneIds, plantIds, null, null, false, false);

        // Event type breakdown (overall)
        Map<String, Long> eventsByTypeLong = plantEventRepository.countByEventTypeForProfile(
                plotIds, zoneIds, plantIds);
        Map<String, Integer> eventsByType = new LinkedHashMap<>();
        eventsByTypeLong.forEach((k, v) -> eventsByType.put(k, v.intValue()));

        // Event type breakdown (this month)
        Map<String, Long> monthEventsByTypeLong = plantEventRepository.countByEventTypeForProfile(
                plotIds, zoneIds, plantIds, monthStart, monthEnd);
        Map<String, Integer> monthEventsByType = new LinkedHashMap<>();
        monthEventsByTypeLong.forEach((k, v) -> monthEventsByType.put(k, v.intValue()));

        // ── 4. Plan stats ─────────────────────────────────────────────────────
        long totalPlans = planRepository.countByOwnerIdAndActiveTrue(profileId);
        long activePlanApplies = planApplyRepository.findByAppliedById(profileId).stream()
                .filter(a -> a.getStatus() == PlanStatus.ACTIVE || a.getStatus() == PlanStatus.APPLYING)
                .count();
        long completedPlanApplies = planApplyRepository.findByAppliedById(profileId).stream()
                .filter(a -> a.getStatus() == PlanStatus.COMPLETED)
                .count();

        // ── 5. Recent activity ────────────────────────────────────────────────
        List<PlantEvent> recentEvents = plantEventRepository.findRecentProfileEvents(
                plotIds, zoneIds, plantIds, 5);
        List<RecentEventSummary> recentSummaries = recentEvents.stream()
                .map(e -> RecentEventSummary.builder()
                        .id(e.getId())
                        .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                        .note(e.getNote())
                        .targetType(e.getTargetType() != null ? e.getTargetType().name() : null)
                        .completed(e.isCompleted())
                        .calculatedStartDate(e.getCalculatedStartDate())
                        .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                        .build())
                .toList();

        // ── Build response ────────────────────────────────────────────────────
        return AgricultureStatsResponse.builder()
                .totalFarmPlots(plots.size())
                .totalFarmZones(zones.size())
                .totalAreaM2(totalArea)
                .totalPlants(plants.size())
                .activePlants(activePlants)
                .inactivePlants(inactivePlants)
                .archivedPlants(archivedPlants)
                .todayEvents((int) todayEvents)
                .todayCompletedEvents((int) todayCompleted)
                .monthEvents((int) monthEvents)
                .monthCompletedEvents((int) monthCompleted)
                .monthPendingEvents((int) monthPending)
                .upcomingEvents7d((int) upcoming7d)
                .overdueEvents((int) overdue)
                .totalCompletedEvents((int) totalCompleted)
                .totalPendingEvents((int) totalPending)
                .eventsByType(eventsByType)
                .monthEventsByType(monthEventsByType)
                .totalPlans((int) totalPlans)
                .activePlanApplies((int) activePlanApplies)
                .completedPlanApplies((int) completedPlanApplies)
                .recentEvents(recentSummaries)
                .build();
    }
}
