package com.leafy.plantmanagementservice.repository.custom;

import com.leafy.plantmanagementservice.model.FarmZone;
import com.leafy.plantmanagementservice.model.enums.FarmZoneStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@RequiredArgsConstructor
public class FarmZoneRepositoryCustomImpl implements FarmZoneRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<FarmZone> findZonesFiltered(
            String searchTerm,
            FarmZoneStatus status,
            String cropType,
            String soilType,
            Double minAreaM2,
            Double maxAreaM2,
            Pageable pageable) {

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("active").is(true));

        if (searchTerm != null && !searchTerm.isBlank()) {
            criteria.add(new Criteria().orOperator(
                    Criteria.where("zoneName").regex(searchTerm, "i"),
                    Criteria.where("zoneCode").regex(searchTerm, "i")
            ));
        }
        if (status != null) {
            criteria.add(Criteria.where("status").is(status));
        }
        if (cropType != null && !cropType.isBlank()) {
            criteria.add(Criteria.where("cropType").regex(cropType, "i"));
        }
        if (soilType != null && !soilType.isBlank()) {
            criteria.add(Criteria.where("soilType").regex(soilType, "i"));
        }
        if (minAreaM2 != null) {
            criteria.add(Criteria.where("areaM2").gte(minAreaM2));
        }
        if (maxAreaM2 != null) {
            criteria.add(Criteria.where("areaM2").lte(maxAreaM2));
        }

        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, FarmZone.class);
        query.with(pageable);
        List<FarmZone> results = mongoTemplate.find(query, FarmZone.class);

        return new PageImpl<>(results, pageable, total);
    }
}
