package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.Vote;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteRepository extends MongoRepository<Vote, String> {
    Optional<Vote> findByAuthorIdAndTargetIdAndTargetType(String authorId, String targetId, VoteTargetType targetType);
}
