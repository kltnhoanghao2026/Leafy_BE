package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.ViewedPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ViewedPostRepository extends MongoRepository<ViewedPost, String> {

    List<ViewedPost> findByUserId(String userId);

    List<ViewedPost> findByUserIdAndPostIdIn(String userId, Collection<String> postIds);

    @Query("{ 'userId': ?0 }")
    List<String> findPostIdsByUserId(String userId);

    boolean existsByUserIdAndPostId(String userId, String postId);

    void deleteByUserIdAndPostId(String userId, String postId);

    void deleteAllByUserIdAndPostIdIn(String userId, Collection<String> postIds);
}
