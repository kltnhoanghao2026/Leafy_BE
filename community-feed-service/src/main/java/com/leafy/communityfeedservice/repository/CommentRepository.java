package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {
    
    Page<Comment> findByPostIdAndParentIdIsNullAndActiveTrue(String postId, Pageable pageable);
    
    Page<Comment> findByParentIdAndActiveTrue(String parentId, Pageable pageable);
    
    long countByPostIdAndActiveTrue(String postId);
    
    long countByParentIdAndActiveTrue(String parentId);
}
