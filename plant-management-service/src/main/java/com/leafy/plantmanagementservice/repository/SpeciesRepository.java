package com.leafy.plantmanagementservice.repository;

import com.leafy.plantmanagementservice.model.Species;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpeciesRepository extends MongoRepository<Species, String> {
    Optional<Species> findByCommonName(String commonName);
}
