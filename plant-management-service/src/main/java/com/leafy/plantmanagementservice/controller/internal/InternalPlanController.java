package com.leafy.plantmanagementservice.controller.internal;

import com.leafy.common.dto.ApiResponse;
import com.leafy.plantmanagementservice.dto.response.plan.PlanResponse;
import com.leafy.plantmanagementservice.mapper.PlanMapper;
import com.leafy.plantmanagementservice.model.Plan;
import com.leafy.plantmanagementservice.repository.PlanApplyRepository;
import com.leafy.plantmanagementservice.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Internal endpoints used by the search-service Feign client
 * to fetch Plan data for Elasticsearch indexing.
 */
@RestController
@RequestMapping("/internal/plans")
@RequiredArgsConstructor
@Slf4j
public class InternalPlanController {

    private final PlanRepository planRepository;
    private final PlanApplyRepository planApplyRepository;
    private final PlanMapper planMapper;

    @GetMapping("/{planId}")
    public ApiResponse<PlanResponse> getPlanById(@PathVariable String planId) {
        log.debug("GET /internal/plans/{}", planId);
        Plan plan = planRepository.findById(planId)
                .orElse(null);
        if (plan == null) {
            return ApiResponse.success(null);
        }
        PlanResponse response = planMapper.toResponse(plan);
        enrichWithApplyCounts(response);
        return ApiResponse.success(response);
    }

    @GetMapping("/batch")
    public ApiResponse<List<PlanResponse>> getPlansBatch(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "200") int size) {
        log.debug("GET /internal/plans/batch page={} size={}", page, size);
        Page<Plan> plans = planRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
        List<PlanResponse> responses = plans.getContent().stream()
                .map(planMapper::toResponse)
                .peek(this::enrichWithApplyCounts)
                .toList();
        return ApiResponse.success(responses);
    }

    private void enrichWithApplyCounts(PlanResponse response) {
        if (response.getId() == null) return;
        response.setApplyCount(planApplyRepository.countByPlanId(response.getId()));
        response.setSuccessApplyCount(planApplyRepository.countByPlanIdAndSuccess(response.getId(), true));
        response.setFailedApplyCount(planApplyRepository.countByPlanIdAndSuccess(response.getId(), false));
    }
}
