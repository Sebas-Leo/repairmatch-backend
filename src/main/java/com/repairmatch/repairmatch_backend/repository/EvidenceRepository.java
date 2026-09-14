package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Evidence;
import com.repairmatch.repairmatch_backend.model.EvidenceID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, EvidenceID> {
    List<Evidence> findByRequestId(Long requestId);
}
