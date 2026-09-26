package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.*;
import com.repairmatch.repairmatch_backend.exception.*;
import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.*;
import com.repairmatch.repairmatch_backend.security.AccountAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ServiceService {
    private final ServiceRepository serviceRepository;
    private final ReviewRepository reviewRepository;
    private final TechnicianRepository technicianRepository;
    private final AccountAccess accountAccess;

    @Transactional(readOnly = true)
    public List<ServiceResponseDTO> getOwnServices() {
        UUID user = accountAccess.requireIdentity();
        return serviceRepository.findOwn(user).stream().map(ServiceResponseDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponseDTO getServiceById(Long id) {
        ServiceEntity service = serviceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        requireParticipant(service);
        return new ServiceResponseDTO(service);
    }

    @Transactional
    public ServiceResponseDTO updateServiceStatus(Long id, ServiceEntity.ServiceStatus next) {
        ServiceEntity service = serviceRepository.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        requireParticipant(service);
        var current = service.getStatus();
        if (next == ServiceEntity.ServiceStatus.CANCELADO) {
            if (current != ServiceEntity.ServiceStatus.PROGRAMADO) throw new InvalidStateException("Only scheduled services can be cancelled");
        } else {
            UUID technician = accountAccess.requireRole(Role.TECHNICIAN);
            accountAccess.requireOwner(technician, service.getProposal().getTechnician().getId());
            boolean valid = current == ServiceEntity.ServiceStatus.PROGRAMADO && next == ServiceEntity.ServiceStatus.EN_ATENCION
                    || current == ServiceEntity.ServiceStatus.EN_ATENCION && next == ServiceEntity.ServiceStatus.COMPLETADO;
            if (!valid) throw new InvalidStateException("Invalid service transition");
        }
        service.setStatus(next);
        return new ServiceResponseDTO(service);
    }

    @Transactional
    public void createReview(Long serviceId, ReviewRequestDTO dto) {
        UUID client = accountAccess.requireRole(Role.CLIENT);
        ServiceEntity service = serviceRepository.findLockedById(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        accountAccess.requireOwner(client, service.getRequest().getClient().getId());
        if (service.getStatus() != ServiceEntity.ServiceStatus.COMPLETADO) throw new InvalidStateException("Only completed services can be reviewed");
        if (reviewRepository.findByServiceId(serviceId).isPresent()) throw new InvalidStateException("Service already reviewed");
        ReviewEntity review = new ReviewEntity();
        review.setService(service); review.setRating(dto.getRating()); review.setComment(dto.getComment().strip());
        reviewRepository.saveAndFlush(review);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTechnicianReputation(UUID technicianId) {
        if (!technicianRepository.existsById(technicianId)) throw new ResourceNotFoundException("Technician not found");
        Double average = reviewRepository.averageForTechnician(technicianId);
        return Map.of("technicianId", technicianId, "averageRating", average == null ? 0.0 : average,
                "reviewCount", reviewRepository.countByServiceProposalTechnicianId(technicianId));
    }

    private void requireParticipant(ServiceEntity service) {
        UUID user = accountAccess.requireIdentity();
        if (!user.equals(service.getRequest().getClient().getId()) && !user.equals(service.getProposal().getTechnician().getId()))
            throw new ForbiddenOperationException("Only service participants may access this resource");
    }
}
