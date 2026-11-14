package com.repairmatch.repairmatch_backend.dto;

import com.repairmatch.repairmatch_backend.model.ServiceEntity;
import java.time.LocalDateTime;
import java.util.UUID;

public class ServiceResponseDTO {

    private Long id;
    private Long proposalId;
    private ServiceEntity.ServiceStatus status;
    private LocalDateTime createdAt;
    // Derived at read time through Service -> Proposal / Request; never stored on the service.
    private Long requestId;
    private UUID clientId;
    private UUID technicianId;
    private String technicianName;

    public ServiceResponseDTO(ServiceEntity service) {
        this.id = service.getId();
        this.proposalId = service.getProposalId();
        this.status = service.getStatus();
        this.createdAt = service.getCreatedAt();
        this.requestId = service.getRequest().getId();
        this.clientId = service.getRequest().getClient().getId();
        this.technicianId = service.getProposal().getTechnician().getId();
        this.technicianName = service.getProposal().getTechnician().getName();
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

    public Long getRequestId() { return requestId; }
    public UUID getClientId() { return clientId; }
    public UUID getTechnicianId() { return technicianId; }
    public String getTechnicianName() { return technicianName; }
}
