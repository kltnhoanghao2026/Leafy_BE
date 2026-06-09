package com.leafy.plantmanagementservice.scheduler;

import com.leafy.plantmanagementservice.model.PlanApply;
import com.leafy.plantmanagementservice.model.PlantEvent;
import com.leafy.plantmanagementservice.model.enums.PlanStatus;
import com.leafy.plantmanagementservice.repository.PlanApplyRepository;
import com.leafy.plantmanagementservice.repository.PlantEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that automatically marks ACTIVE plan applies as COMPLETED
 * when every applied event (stored in {@link PlanApply#getPlantEventIds()})
 * has an end date (or start date when no end date) that is before today.
 *
 * <p>Runs daily at 01:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanAutoCompleteScheduler {

    private final PlanApplyRepository planApplyRepository;
    private final PlantEventRepository plantEventRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void autoCompletePlanApplies() {
        LocalDate today = LocalDate.now();
        List<PlanApply> activeApplies = planApplyRepository.findByStatus(PlanStatus.ACTIVE);
        log.info("PlanAutoCompleteScheduler: checking {} ACTIVE applies against date {}", activeApplies.size(), today);

        int completed = 0;
        for (PlanApply apply : activeApplies) {
            try {
                if (apply.getPlantEventIds() == null || apply.getPlantEventIds().isEmpty()) {
                    continue;
                }

                List<PlantEvent> appliedEvents = (List<PlantEvent>) plantEventRepository.findAllById(apply.getPlantEventIds());
                if (appliedEvents.isEmpty()) {
                    continue;
                }

                boolean allPast = appliedEvents.stream().allMatch(e -> {
                    // Prefer calculatedEndDate; fall back to calculatedStartDate
                    LocalDate checkDate = e.getCalculatedEndDate() != null
                            ? e.getCalculatedEndDate()
                            : e.getCalculatedStartDate();
                    return checkDate != null && checkDate.isBefore(today);
                });

                if (allPast) {
                    apply.setStatus(PlanStatus.COMPLETED);
                    planApplyRepository.save(apply);
                    completed++;
                    log.info("PlanApply id={} (planId={}) auto-completed ({} events all past)",
                            apply.getId(), apply.getPlanId(), appliedEvents.size());
                }
            } catch (Exception e) {
                log.warn("Error auto-completing apply id={}: {}", apply.getId(), e.getMessage());
            }
        }
        log.info("PlanAutoCompleteScheduler: completed {} applies", completed);
    }
}
