package com.leafy.fileservice.repository;

import com.leafy.fileservice.model.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends MongoRepository<File, String> {

    /**
     * Find file by S3 key
     *
     * @param s3Key the S3 key to search for
     * @return optional file
     */
    Optional<File> findByS3Key(String s3Key);

    /**
     * Find files by uploader
     *
     * @param uploadedBy the user ID who uploaded the file
     * @return list of files
     */
    List<File> findByUploadedBy(String uploadedBy);

    /**
     * Find files by uploader with pagination
     *
     * @param uploadedBy the user ID who uploaded the file
     * @param pageable   pagination information
     * @return page of files
     */
    Page<File> findByUploadedBy(String uploadedBy, Pageable pageable);

    /**
     * Find files by content type
     *
     * @param contentType the content type to search for
     * @return list of files
     */
    List<File> findByContentType(String contentType);

    /**
     * Find all active files with pagination
     *
     * @param pageable pagination information
     * @return page of active files
     */
    Page<File> findByActiveTrue(Pageable pageable);

    /**
     * Search files by original filename containing search term
     *
     * @param searchTerm the search term
     * @param pageable   pagination information
     * @return page of matching files
     */
    @Query("{ 'originalFileName': { '$regex': ?0, '$options': 'i' } }")
    Page<File> searchByOriginalFileName(String searchTerm, Pageable pageable);

    /**
     * Check if S3 key exists
     *
     * @param s3Key the S3 key to check
     * @return true if exists, false otherwise
     */
    boolean existsByS3Key(String s3Key);

    /**
     * Count files by uploader
     *
     * @param uploadedBy the user ID who uploaded the file
     * @return count of files
     */
    long countByUploadedBy(String uploadedBy);

    /**
     * Count active files
     *
     * @return count of active files
     */
    long countByActiveTrue();
}
