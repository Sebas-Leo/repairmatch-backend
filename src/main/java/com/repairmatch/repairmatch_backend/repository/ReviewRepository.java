package com.repairmatch.repairmatch_backend.repository;

import com.repairmatch.repairmatch_backend.model.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    // Buscar una reseña asociada a un servicio específico
    Optional<ReviewEntity> findByServiceId(Long serviceId);

    // Buscar todas las reseñas de un técnico para calcular su reputación promedio
    List<ReviewEntity> findByTechnicianId(Long technicianId);
}