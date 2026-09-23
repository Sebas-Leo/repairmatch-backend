package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
}