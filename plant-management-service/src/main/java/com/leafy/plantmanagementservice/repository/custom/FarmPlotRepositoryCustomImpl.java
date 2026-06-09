package com.leafy.plantmanagementservice.repository.custom;

import com.leafy.plantmanagementservice.model.FarmPlot;
import com.leafy.plantmanagementservice.model.enums.FarmPlotStatus;
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
public class FarmPlotRepositoryCustomImpl implements FarmPlotRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<FarmPlot> findPlotsFiltered(
            String searchTerm,
            FarmPlotStatus status,
            String provinceCode,
            Double minAreaM2,
            Double maxAreaM2,
            Pageable pageable) {

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        // Always filter to active records (soft-delete)
        criteria.add(Criteria.where("active").is(true));

        if (searchTerm != null && !searchTerm.isBlank()) {
            criteria.add(new Criteria().orOperator(
                    Criteria.where("name").regex(searchTerm, "i"),
                    Criteria.where("code").regex(searchTerm, "i"),
                    Criteria.where("addressLine").regex(searchTerm, "i")
            ));
        }
        if (status != null) {
            criteria.add(Criteria.where("status").is(status));
        }
        if (provinceCode != null && !provinceCode.isBlank()) {
            criteria.add(Criteria.where("provinceCode").is(provinceCode));
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

        long total = mongoTemplate.count(query, FarmPlot.class);
        query.with(pageable);
        List<FarmPlot> results = mongoTemplate.find(query, FarmPlot.class);

        return new PageImpl<>(results, pageable, total);
    }
}
