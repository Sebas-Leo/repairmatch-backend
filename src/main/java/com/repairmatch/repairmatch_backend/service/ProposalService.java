package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.ProposalResponseDto;
import com.repairmatch.repairmatch_backend.model.Proposal;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.repository.ProposalRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.security.AccountAccess;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final com.repairmatch.repairmatch_backend.repository.TechnicianRepository technicianRepository;
    private final com.repairmatch.repairmatch_backend.repository.UserRepository userRepository;
    private final com.repairmatch.repairmatch_backend.repository.ServiceRepository serviceRepository;
    private final CompatibilityPolicy compatibilityPolicy;
    private final AccountAccess accountAccess;
    private final RequestRepository requestRepository;
    private final ProposalRepository proposalRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CLIENT')")
    public List<ProposalResponseDto> compareByRequest(Long requestId) {
        UUID currentUserId = accountAccess.requireRole(Role.CLIENT);
        Request request = requestRepository.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La solicitud especificada no existe"
                ));

        if (!request.getClient().getId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tiene autorización para consultar las propuestas de esta solicitud"
            );
        }

        return proposalRepository
                .findByRequestIdOrderByDiagnosticCostAscAvailableAtAsc(requestId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ProposalResponseDto submit(Long requestId, com.repairmatch.repairmatch_backend.dto.CreateProposalDto dto) {
        UUID id = accountAccess.requireRole(Role.TECHNICIAN);
        Request request = requestRepository.findLockedById(requestId).orElseThrow(() -> new com.repairmatch.repairmatch_backend.exception.ResourceNotFoundException("Request not found"));
        if (!CompatibilityPolicy.isOpen(request)) throw new com.repairmatch.repairmatch_backend.exception.InvalidStateException("Request no longer accepts proposals");
        var technician = technicianRepository.findByIdWithApplianceTypes(id).orElseThrow(() -> new com.repairmatch.repairmatch_backend.exception.IncompleteProfileException("Configure the technician profile first"));
        if (!compatibilityPolicy.matches(technician, request)) throw new com.repairmatch.repairmatch_backend.exception.ForbiddenOperationException("Technician is not compatible with this request");
        if (proposalRepository.existsByRequestIdAndTechnicianId(requestId, id)) throw new com.repairmatch.repairmatch_backend.exception.InvalidStateException("Only one proposal per technician and request is allowed");
        Proposal proposal = proposalRepository.saveAndFlush(new Proposal(request, technician.getUser(), dto.diagnosticCost(), dto.availableAt(), dto.conditions().strip()));
        request.setStatus(Request.RequestStatus.CON_PROPUESTAS);
        return toResponse(proposal);
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public com.repairmatch.repairmatch_backend.dto.ServiceResponseDTO accept(Long proposalId) {
        UUID client = accountAccess.requireRole(Role.CLIENT);
        Proposal selected = proposalRepository.findById(proposalId).orElseThrow(() -> new com.repairmatch.repairmatch_backend.exception.ResourceNotFoundException("Proposal not found"));
        Request request = requestRepository.findLockedById(selected.getRequest().getId()).orElseThrow(() -> new com.repairmatch.repairmatch_backend.exception.ResourceNotFoundException("Request not found"));
        accountAccess.requireOwner(client, request.getClient().getId());
        if (!CompatibilityPolicy.isOpen(request) || selected.getStatus() != Proposal.Status.PENDING)
            throw new com.repairmatch.repairmatch_backend.exception.InvalidStateException("Proposal cannot be accepted");
        if (!selected.getAvailableAt().isAfter(java.time.LocalDateTime.now()))
            throw new com.repairmatch.repairmatch_backend.exception.InvalidStateException("Proposal availability has passed");
        // Locking the request serializes every decision, including cancellation and competing acceptances.
        for (Proposal proposal : proposalRepository.findByRequestIdOrderByDiagnosticCostAscAvailableAtAsc(request.getId()))
            proposal.setStatus(proposal.getId().equals(proposalId) ? Proposal.Status.ACCEPTED : Proposal.Status.REJECTED);
        request.setStatus(Request.RequestStatus.CERRADA);
        var service = new com.repairmatch.repairmatch_backend.model.ServiceEntity();
        service.setProposal(selected); service.setRequest(request);
        serviceRepository.saveAndFlush(service);
        return new com.repairmatch.repairmatch_backend.dto.ServiceResponseDTO(service);
    }

    private ProposalResponseDto toResponse(Proposal proposal) {
        return new ProposalResponseDto(
                proposal.getId(),
                proposal.getRequest().getId(),
                proposal.getTechnician().getId(),
                proposal.getTechnician().getName(),
                proposal.getDiagnosticCost(),
                proposal.getAvailableAt(),
                proposal.getConditions(),
                proposal.getCreatedAt(), proposal.getStatus().name()
        );
    }
}
