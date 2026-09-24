package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Evidence;
import com.repairmatch.repairmatch_backend.model.EvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, EvidenceId> {
    List<Evidence> findByRequestId(Long requestId);
    List<Evidence> findById_RequestIdOrderById_EvidenceNumberAsc(Long requestId);

    @Query("select coalesce(max(e.id.evidenceNumber), 0) from Evidence e where e.id.requestId = :requestId")
    int maxEvidenceNumber(@Param("requestId") Long requestId);
}
