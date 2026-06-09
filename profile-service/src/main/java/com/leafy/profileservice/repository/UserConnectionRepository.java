package com.leafy.profileservice.repository;

import com.leafy.profileservice.model.UserConnection;
import com.leafy.profileservice.model.enums.ConsultationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConnectionRepository extends MongoRepository<UserConnection, String> {
    
    Optional<UserConnection> findByFollowerIdAndFollowingId(String followerId, String followingId);
    
    Page<UserConnection> findAllByFollowerIdAndIsFollowingTrue(String followerId, Pageable pageable);
    
    Page<UserConnection> findAllByFollowingIdAndIsFollowingTrue(String followingId, Pageable pageable);
    
    Page<UserConnection> findAllByFollowingIdAndConsultationStatus(String followingId, ConsultationStatus consultationStatus, Pageable pageable);

    List<UserConnection> findAllByFollowerIdAndFollowingIdIn(String followerId, java.util.List<String> followingIds);

    List<UserConnection> findByFollowingIdAndConsultationStatus(String followingId, ConsultationStatus consultationStatus);
}
