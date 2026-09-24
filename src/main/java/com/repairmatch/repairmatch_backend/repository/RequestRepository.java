package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Request;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
    @EntityGraph(attributePaths = {"client", "applianceType"})
    List<Request> findByClientIdOrderByCreatedAtDesc(UUID clientId);
    List<Request> findByApplianceTypeId(Long applianceTypeId);

    @EntityGraph(attributePaths = {"client", "applianceType"})
    @Query("select r from Request r where r.id = :id")
    Optional<Request> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Request r where r.id = :id")
    Optional<Request> findLockedById(@Param("id") Long id);
}
