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

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final RequestRepository requestRepository;
    private final ProposalRepository proposalRepository;

    @Transactional(readOnly = true)
    public List<ProposalResponseDto> compareByRequest(Long requestId, UUID currentUserId) {
        Request request = requestRepository.findById(requestId)
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

    private ProposalResponseDto toResponse(Proposal proposal) {
        return new ProposalResponseDto(
                proposal.getId(),
                proposal.getRequest().getId(),
                proposal.getTechnician().getId(),
                proposal.getTechnician().getName(),
                proposal.getDiagnosticCost(),
                proposal.getAvailableAt(),
                proposal.getConditions(),
                proposal.getCreatedAt()
        );
    }
}
