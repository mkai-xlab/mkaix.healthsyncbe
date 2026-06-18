package com.g93.be.repository;

import com.g93.be.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {
    Optional<Feature> findByName(String name);
    boolean existsByName(String name);
}
