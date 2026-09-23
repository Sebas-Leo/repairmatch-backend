package com.repairmatch.repairmatch_backend.dto;

import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import java.time.LocalDateTime;

public class ServiceResponseDTO {

    private Long id;
    private Long proposalId;
    private ServiceEntity.ServiceStatus status;
    private LocalDateTime createdAt;

    public ServiceResponseDTO(ServiceEntity service) {
        this.id = service.getId();
        this.proposalId = service.getProposalId();
        this.status = service.getStatus();
        this.createdAt = service.getCreatedAt();
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }

    public ServiceEntity.ServiceStatus getStatus() { return status; }
    public void setStatus(ServiceEntity.ServiceStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}