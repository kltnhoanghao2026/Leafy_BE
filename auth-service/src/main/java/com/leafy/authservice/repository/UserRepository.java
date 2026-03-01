package com.leafy.authservice.repository;

import com.leafy.authservice.model.User;
import com.leafy.common.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Find user by email
     *
     * @param email the email to search for
     * @return optional user
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by phone number
     *
     * @param phoneNumber the phone number to search for
     * @return optional user
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Find user by email or phone number
     *
     * @param email       the email to search for
     * @param phoneNumber the phone number to search for
     * @return optional user
     */
    Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);

    /**
     * Check if email exists
     *
     * @param email the email to check
     * @return true if exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Check if phone number exists
     *
     * @param phoneNumber the phone number to check
     * @return true if exists, false otherwise
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find all active users
     *
     * @return list of active users
     */
    List<User> findByActiveTrue();

    /**
     * Find all users by role
     *
     * @param role the role to filter by
     * @return list of users with the specified role
     */
    List<User> findByRole(Role role);

    /**
     * Find all users by role with pagination
     *
     * @param role     the role to filter by
     * @param pageable pagination information
     * @return page of users with the specified role
     */
    Page<User> findByRole(Role role, Pageable pageable);

    /**
     * Find all active users with pagination
     *
     * @param pageable pagination information
     * @return page of active users
     */
    Page<User> findByActiveTrue(Pageable pageable);

    /**
     * Search users by email or phone number containing search term
     *
     * @param email       email search term
     * @param phoneNumber phone number search term
     * @param pageable    pagination information
     * @return page of matching users
     */
    @Query("{ '$or': [ { 'email': { '$regex': ?0, '$options': 'i' } }, { 'phoneNumber': { '$regex': ?1, '$options': 'i' } } ] }")
    Page<User> searchByEmailOrPhoneNumber(String email, String phoneNumber, Pageable pageable);

    /**
     * Find users by role and active status
     *
     * @param role     the role to filter by
     * @param active   the active status to filter by
     * @param pageable pagination information
     * @return page of users matching the criteria
     */
    Page<User> findByRoleAndActive(Role role, boolean active, Pageable pageable);

    /**
     * Count users by role
     *
     * @param role the role to count
     * @return count of users with the specified role
     */
    long countByRole(Role role);

    /**
     * Count active users
     *
     * @return count of active users
     */
    long countByActiveTrue();
}
