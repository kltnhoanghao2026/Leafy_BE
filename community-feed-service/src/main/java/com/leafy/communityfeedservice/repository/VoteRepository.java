package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.Vote;
import com.leafy.communityfeedservice.model.enums.VoteTargetType;
import com.leafy.communityfeedservice.model.enums.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends MongoRepository<Vote, String> {
    Optional<Vote> findByAuthorIdAndTargetIdAndTargetType(String authorId, String targetId, VoteTargetType targetType);

    List<Vote> findByAuthorIdAndTargetIdInAndTargetTypeAndActiveTrue(String authorId, List<String> targetIds, VoteTargetType targetType);

    Page<Vote> findByTargetIdAndTargetTypeAndTypeAndActiveTrue(
            String targetId,
            VoteTargetType targetType,
            VoteType type,
            Pageable pageable);
}
