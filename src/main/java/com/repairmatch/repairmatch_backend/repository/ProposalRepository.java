package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    /** Rows of [requestId, proposalCount] for the given requests. */
    @org.springframework.data.jpa.repository.Query(
            "select p.request.id, count(p) from Proposal p where p.request.id in :ids group by p.request.id")
    List<Object[]> countByRequestIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    @org.springframework.data.jpa.repository.Query("select p.request.id from Proposal p where p.technician.id = :id")
    List<Long> requestIdsProposedBy(@org.springframework.data.repository.query.Param("id") java.util.UUID technicianId);

    boolean existsByRequestIdAndTechnicianId(Long requestId, java.util.UUID technicianId);

    boolean existsByRequestIdAndTechnicianIdAndStatus(Long requestId, java.util.UUID technicianId, Proposal.Status status);

    @EntityGraph(attributePaths = {"technician", "request"})
    List<Proposal> findByRequestIdOrderByDiagnosticCostAscAvailableAtAsc(Long requestId);

    @EntityGraph(attributePaths = {"technician", "request"})
    org.springframework.data.domain.Page<Proposal> findByTechnicianId(
            java.util.UUID technicianId, org.springframework.data.domain.Pageable pageable);
}
