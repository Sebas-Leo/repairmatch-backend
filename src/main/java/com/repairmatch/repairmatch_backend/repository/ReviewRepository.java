package com.repairmatch.repairmatch_backend.repository;
import com.repairmatch.repairmatch_backend.model.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    Optional<ReviewEntity> findByServiceId(Long serviceId);
    @Query("select avg(r.rating) from ReviewEntity r where r.service.proposal.technician.id = :id")
    Double averageForTechnician(@Param("id") UUID id);
    long countByServiceProposalTechnicianId(UUID id);
    @Query("select r from ReviewEntity r join fetch r.service s where s.proposal.technician.id = :id order by r.createdAt desc, r.id desc")
    List<ReviewEntity> findForTechnician(@Param("id") UUID id);

    /** Rows of [technicianId, averageRating, reviewCount] for the given technicians. */
    @Query("select r.service.proposal.technician.id, avg(r.rating), count(r) from ReviewEntity r "
            + "where r.service.proposal.technician.id in :ids group by r.service.proposal.technician.id")
    List<Object[]> statsForTechnicians(@Param("ids") java.util.Collection<UUID> ids);

    /** Rows of [technicianId, averageRating] of the reviews this client wrote about the given technicians. */
    @Query("select r.service.proposal.technician.id, avg(r.rating) from ReviewEntity r "
            + "where r.service.request.client.id = :client and r.service.proposal.technician.id in :ids "
            + "group by r.service.proposal.technician.id")
    List<Object[]> ratingsByClient(@Param("client") UUID client, @Param("ids") java.util.Collection<UUID> ids);
}
