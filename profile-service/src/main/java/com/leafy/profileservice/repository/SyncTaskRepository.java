package com.leafy.profileservice.repository;

import com.leafy.profileservice.model.SyncTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncTaskRepository extends MongoRepository<SyncTask, String> {
}
