package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.ApplianceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplianceTypeRepository extends JpaRepository<ApplianceType, Long> {
    Optional<ApplianceType> findByNameIgnoreCase(String name);
}