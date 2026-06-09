package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.ViewedPost;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ViewedPostRepository extends MongoRepository<ViewedPost, String> {

    List<ViewedPost> findByUserId(String userId);

    List<ViewedPost> findByUserIdAndPostIdIn(String userId, Collection<String> postIds);

    /**
     * Returns all viewed post IDs for a user.
     * Uses aggregation to project only the postId field.
     */
    @Aggregation(pipeline = {
            "{ $match: { userId: ?0 } }",
            "{ $project: { _id: 0, postId: 1 } }"
    })
    List<String> findPostIdsByUserId(String userId);

    boolean existsByUserIdAndPostId(String userId, String postId);

    void deleteByUserIdAndPostId(String userId, String postId);

    void deleteAllByUserIdAndPostIdIn(String userId, Collection<String> postIds);
}
