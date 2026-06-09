package com.leafy.searchservice.repository;

import com.leafy.searchservice.model.mongo.FailedEvents;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FailedEventRepository extends MongoRepository<FailedEvents, String> {
    long countByResolved(boolean resolved);

    List<FailedEvents> findAllByResolved(boolean resolved);

    List<FailedEvents> findAllByResolvedAndCreatedAtAfter(boolean resolved, LocalDateTime createdAt);
}
