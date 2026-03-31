package com.leafy.farmservice.mapper;

import com.leafy.farmservice.dto.request.farmplot.CreateFarmPlotRequest;
import com.leafy.farmservice.dto.request.farmplot.UpdateFarmPlotRequest;
import com.leafy.farmservice.dto.response.farmplot.FarmPlotResponse;
import com.leafy.farmservice.model.FarmPlot;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FarmPlotMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    FarmPlot toEntity(CreateFarmPlotRequest request);

    FarmPlotResponse toResponse(FarmPlot farmPlot);

    List<FarmPlotResponse> toResponseList(List<FarmPlot> farmPlots);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerUserId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntityFromRequest(UpdateFarmPlotRequest request, @MappingTarget FarmPlot farmPlot);
}
