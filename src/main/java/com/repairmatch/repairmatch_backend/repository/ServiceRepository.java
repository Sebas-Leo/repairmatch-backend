package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ServiceEntity s where s.id = :id")
    Optional<ServiceEntity> findLockedById(@Param("id") Long id);
    @EntityGraph(attributePaths = {"proposal", "proposal.technician", "request", "request.client"})
    @Query("select s from ServiceEntity s where s.request.client.id = :id or s.proposal.technician.id = :id order by s.createdAt desc")
    List<ServiceEntity> findOwn(@Param("id") UUID id);
}
