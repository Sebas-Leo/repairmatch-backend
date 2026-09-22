package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
    List<Request> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<Request> findByApplianceTypeId(Long applianceTypeId);
}
