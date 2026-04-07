package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.enums.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
	Page<Post> findByPostTypeIn(Collection<PostType> postTypes, Pageable pageable);
	Page<Post> findByAuthorId(String authorId, Pageable pageable);
}
