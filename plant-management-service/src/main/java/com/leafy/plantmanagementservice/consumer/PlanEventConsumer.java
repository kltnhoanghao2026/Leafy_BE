package com.leafy.plantmanagementservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.common.event.PlanAppliedEvent;
import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanEventConsumer {

    private final PlanRepository planRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "#{kafkaTopicProperties.systemEvents.planApplied}", groupId = "${spring.application.name}-group")
    @Transactional
    public void handlePlanApplied(@Payload String message) {
        try {
            PlanAppliedEvent event = objectMapper.readValue(message, PlanAppliedEvent.class);
            log.info("Received PlanAppliedEvent for planId={}", event.getPlanId());
            
            planRepository.findById(event.getPlanId()).ifPresent(plan -> {
                int currentCount = plan.getApplyCount() != null ? plan.getApplyCount() : 0;
                plan.setApplyCount(currentCount + 1);
                planRepository.save(plan);
                log.info("Incremented applyCount for Plan id={} to {}", plan.getId(), plan.getApplyCount());
            });
        } catch (Exception e) {
            log.error("Error processing PlanAppliedEvent: {}", e.getMessage(), e);
        }
    }
}
