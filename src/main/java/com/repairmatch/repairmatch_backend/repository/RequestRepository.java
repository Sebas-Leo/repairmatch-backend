package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long>, JpaSpecificationExecutor<Request> {
    @Override
    @EntityGraph(attributePaths = {"client", "applianceType"})
    Page<Request> findAll(Specification<Request> spec, Pageable pageable);

    List<Request> findByApplianceTypeId(Long applianceTypeId);

    @EntityGraph(attributePaths = {"client", "applianceType"})
    /** Requests of the jobs this technician won, except the cancelled ones: the history behind "Para ti". */
    @Query("select r from Request r left join fetch r.applianceType where r.id in "
            + "(select s.request.id from ServiceEntity s where s.proposal.technician.id = :id "
            + "and s.status <> com.repairmatch.repairmatch_backend.model.ServiceEntity.ServiceStatus.CANCELADO)")
    List<Request> findWonBy(@Param("id") UUID technicianId);

    @Query("select r from Request r where r.id = :id")
    Optional<Request> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Request r where r.id = :id")
    Optional<Request> findLockedById(@Param("id") Long id);
}
