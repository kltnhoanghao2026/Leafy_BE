package com.leafy.communityfeedservice.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisibilityMigrationRunner implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        migrateLegacyFriendVisibility();
    }

    private void migrateLegacyFriendVisibility() {
        // Query on raw "visibility" string field without enum conversion
        Query countQuery = new Query(Criteria.where("visibility").is("FRIEND"));
        long count = mongoTemplate.count(countQuery, "posts");

        if (count == 0) {
            log.info("No legacy FRIEND visibility posts found in MongoDB");
            return;
        }

        log.warn("Found {} legacy posts with FRIEND visibility", count);

        // Update using raw string value without enum conversion
        Query updateQuery = new Query(Criteria.where("visibility").is("FRIEND"));
        Update update = new Update().set("visibility", "FOLLOWER");

        var result = mongoTemplate.updateMulti(updateQuery, update, "posts");
        log.info("Migrated {} legacy FRIEND posts to FOLLOWER visibility", result.getModifiedCount());
    }
}
