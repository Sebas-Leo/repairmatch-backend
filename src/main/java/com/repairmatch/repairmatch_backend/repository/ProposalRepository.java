package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    @EntityGraph(attributePaths = {"technician", "request"})
    List<Proposal> findByRequestIdOrderByDiagnosticCostAscAvailableAtAsc(Long requestId);
}
