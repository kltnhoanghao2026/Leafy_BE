package com.leafy.communityfeedservice.repository;

import com.leafy.communityfeedservice.model.Post;
import com.leafy.communityfeedservice.model.enums.PostType;
import com.leafy.communityfeedservice.model.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
	Page<Post> findByPostTypeIn(Collection<PostType> postTypes, Pageable pageable);
	Page<Post> findByAuthorId(String authorId, Pageable pageable);

	Page<Post> findByAuthorIdInAndPostTypeIn(Collection<String> authorIds, Collection<PostType> postTypes, Pageable pageable);

	Page<Post> findByAuthorIdInAndPostTypeInAndIdNotIn(
			Collection<String> authorIds,
			Collection<PostType> postTypes,
			Collection<String> excludeIds,
			Pageable pageable);

	// Visibility-aware queries
	Page<Post> findByAuthorIdInAndPostTypeInAndVisibility(
			Collection<String> authorIds,
			Collection<PostType> postTypes,
			Visibility visibility,
			Pageable pageable);

	Page<Post> findByAuthorIdInAndPostTypeInAndVisibilityAndIdNotIn(
			Collection<String> authorIds,
			Collection<PostType> postTypes,
			Visibility visibility,
			Collection<String> excludeIds,
			Pageable pageable);

	Page<Post> findByAuthorIdInAndPostTypeInAndVisibilityIn(
			Collection<String> authorIds,
			Collection<PostType> postTypes,
			Collection<Visibility> visibilities,
			Pageable pageable);

	Page<Post> findByAuthorIdInAndPostTypeInAndVisibilityInAndIdNotIn(
			Collection<String> authorIds,
			Collection<PostType> postTypes,
			Collection<Visibility> visibilities,
			Collection<String> excludeIds,
			Pageable pageable);

	// Visibility-aware random queries
	@Aggregation(pipeline = {
			"{ $match: { 'postType': { $in: ?0 }, 'visibility': ?1 } }",
			"{ $sample: { size: ?2 } }"
	})
	List<Post> findRandomByPostTypeInAndVisibility(
			Collection<PostType> postTypes,
			Visibility visibility,
			int limit);

	@Aggregation(pipeline = {
			"{ $match: { 'postType': { $in: ?0 }, 'visibility': { $in: ?1 }, '_id': { $nin: ?2 } } }",
			"{ $sample: { size: ?3 } }"
	})
	List<Post> findRandomByPostTypeInAndVisibilityInAndIdNotIn(
			Collection<PostType> postTypes,
			Collection<Visibility> visibilities,
			Collection<String> excludeIds,
			int limit);

	@Aggregation(pipeline = {
			"{ $match: { 'postType': { $in: ?0 } } }",
			"{ $sample: { size: ?1 } }"
	})
	List<Post> findRandomByPostTypeIn(Collection<PostType> postTypes, int limit);

	@Aggregation(pipeline = {
			"{ $match: { 'postType': { $in: ?0 }, '_id': { $nin: ?1 } } }",
			"{ $sample: { size: ?2 } }"
	})
	List<Post> findRandomByPostTypeInAndIdNotIn(
			Collection<PostType> postTypes,
			Collection<String> excludeIds,
			int limit);

	long countByPostTypeIn(Collection<PostType> postTypes);
}
