package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.Technician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    @Query("SELECT t FROM Technician t LEFT JOIN FETCH t.applianceTypes WHERE t.id = :id")
    Optional<Technician> findByIdWithApplianceTypes(@Param("id") UUID id);

    @Query("SELECT t FROM Technician t LEFT JOIN FETCH t.applianceTypes LEFT JOIN FETCH t.user WHERE t.user.email = :email")
    Optional<Technician> findByUserEmail(@Param("email") String email);
}