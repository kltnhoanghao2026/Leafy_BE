package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.ProfileSummary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileSummaryRepository extends MongoRepository<ProfileSummary, String> {
    List<ProfileSummary> findAllByIdIn(List<String> ids);
}
