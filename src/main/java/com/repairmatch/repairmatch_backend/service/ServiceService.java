package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.ReviewRequestDTO;
import com.repairmatch.repairmatch_backend.dto.ServiceResponseDTO;
import com.repairmatch.repairmatch_backend.model.ReviewEntity;
import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import com.repairmatch.repairmatch_backend.repository.ReviewRepository;
import com.repairmatch.repairmatch_backend.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final ReviewRepository reviewRepository;

    public ServiceService(ServiceRepository serviceRepository, ReviewRepository reviewRepository) {
        this.serviceRepository = serviceRepository;
        this.reviewRepository = reviewRepository;
    }

    public ServiceResponseDTO getServiceById(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado con ID: " + id));
        return new ServiceResponseDTO(service);
    }

    public ServiceResponseDTO updateServiceStatus(Long id, ServiceEntity.ServiceStatus newStatus) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado con ID: " + id));

        service.setStatus(newStatus);
        ServiceEntity updated = serviceRepository.save(service);
        return new ServiceResponseDTO(updated);
    }

    public void createReview(Long serviceId, Long clientId, ReviewRequestDTO reviewDTO) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado con ID: " + serviceId));

        if (service.getStatus() != ServiceEntity.ServiceStatus.COMPLETADO) {
            throw new RuntimeException("Solo se pueden calificar servicios que estén en estado COMPLETADO");
        }

        if (reviewRepository.findByServiceId(serviceId).isPresent()) {
            throw new RuntimeException("Este servicio ya cuenta con una reseña registrada");
        }

        ReviewEntity review = new ReviewEntity();
        review.setServiceId(serviceId);
        review.setClientId(clientId);
        review.setTechnicianId(1L); // Asignamos el técnico 1 para que coincida con tu endpoint de reputación
        review.setRating(reviewDTO.getRating());
        review.setComment(reviewDTO.getComment());

        reviewRepository.save(review);
    }

    public double getTechnicianReputation(Long technicianId) {
        List<ReviewEntity> reviews = reviewRepository.findByTechnicianId(technicianId);
        if (reviews.isEmpty()) {
            return 0.0;
        }
        double sum = reviews.stream().mapToInt(ReviewEntity::getRating).sum();
        return sum / reviews.size();
    }
}