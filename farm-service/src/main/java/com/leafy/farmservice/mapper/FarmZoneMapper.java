package com.leafy.farmservice.mapper;

import com.leafy.farmservice.dto.request.farmzone.CreateFarmZoneRequest;
import com.leafy.farmservice.dto.request.farmzone.UpdateFarmZoneRequest;
import com.leafy.farmservice.dto.response.farmzone.FarmZoneResponse;
import com.leafy.farmservice.model.FarmZone;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface FarmZoneMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "farmPlotId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    FarmZone toEntity(CreateFarmZoneRequest request);

    FarmZoneResponse toResponse(FarmZone farmZone);

    List<FarmZoneResponse> toResponseList(List<FarmZone> farmZones);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "farmPlotId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntityFromRequest(UpdateFarmZoneRequest request, @MappingTarget FarmZone farmZone);
}
