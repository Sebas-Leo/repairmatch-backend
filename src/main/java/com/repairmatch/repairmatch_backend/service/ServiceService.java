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
    private final RequestRepository requestRepository;
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

    /** The service created by accepting a proposal of the caller's own request. */
    @Transactional(readOnly = true)
    public ServiceResponseDTO getServiceByRequest(Long requestId) {
        UUID client = accountAccess.requireRole(Role.CLIENT);
        Request request = requestRepository.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        accountAccess.requireOwner(client, request.getClient().getId());
        return serviceRepository.findByRequestId(requestId).map(ServiceResponseDTO::new)
                .orElseThrow(() -> new ResourceNotFoundException("No service exists for this request yet"));
    }

    @Transactional
    public ReviewResponseDto createReview(Long serviceId, ReviewRequestDTO dto) {
        UUID client = accountAccess.requireRole(Role.CLIENT);
        ServiceEntity service = serviceRepository.findLockedById(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        accountAccess.requireOwner(client, service.getRequest().getClient().getId());
        if (service.getStatus() != ServiceEntity.ServiceStatus.COMPLETADO) throw new InvalidStateException("Only completed services can be reviewed");
        if (reviewRepository.findByServiceId(serviceId).isPresent()) throw new InvalidStateException("Service already reviewed");
        ReviewEntity review = new ReviewEntity();
        review.setService(service); review.setRating(dto.getRating()); review.setComment(dto.getComment().strip());
        return ReviewResponseDto.from(reviewRepository.saveAndFlush(review));
    }

    /** The review of a service, readable by its two participants only. */
    @Transactional(readOnly = true)
    public ReviewResponseDto getReview(Long serviceId) {
        ServiceEntity service = serviceRepository.findById(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        requireParticipant(service);
        return reviewRepository.findByServiceId(serviceId).map(ReviewResponseDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("This service has not been reviewed yet"));
    }

    /** Public reputation detail: every review of the technician's completed jobs, newest first. */
    @Transactional(readOnly = true)
    public List<ReviewResponseDto> getTechnicianReviews(UUID technicianId) {
        accountAccess.requireIdentity();
        if (!technicianRepository.existsById(technicianId)) throw new ResourceNotFoundException("Technician not found");
        return reviewRepository.findForTechnician(technicianId).stream().map(ReviewResponseDto::from).toList();
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
