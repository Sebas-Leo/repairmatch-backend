package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.CreateEvidenceDto;
import com.repairmatch.repairmatch_backend.dto.EvidenceResponseDto;
import com.repairmatch.repairmatch_backend.dto.RequestResponseDto;
import com.repairmatch.repairmatch_backend.model.Evidence;
import com.repairmatch.repairmatch_backend.model.EvidenceId;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.repository.EvidenceRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.security.AccountAccess;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final EvidenceRepository evidenceRepository;
    private final AccountAccess accountAccess;

    @Transactional
    public EvidenceResponseDto addEvidence(Long requestId, CreateEvidenceDto dto) {
        Request request = getOwnedRequest(requestId);

        List<Evidence> existingEvidences = evidenceRepository.findByRequestId(requestId);
        int nextEvidenceNumber = existingEvidences.size() + 1;

        EvidenceId evidenceId = new EvidenceId(requestId, nextEvidenceNumber);
        Evidence evidence = Evidence.builder()
                .id(evidenceId)
                .request(request)
                .mediaUrl(dto.getMediaUrl())
                .mediaType(dto.getMediaType())
                .build();

        Evidence saved = evidenceRepository.save(evidence);

        return EvidenceResponseDto.builder()
                .requestId(saved.getId().getRequestId())
                .evidenceNumber(saved.getId().getEvidenceNumber())
                .mediaUrl(saved.getMediaUrl())
                .mediaType(saved.getMediaType())
                .build();
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponseDto> getEvidencesByRequestId(Long requestId) {
        getOwnedRequest(requestId);

        return evidenceRepository.findByRequestId(requestId).stream()
                .map(evidence -> EvidenceResponseDto.builder()
                        .requestId(evidence.getId().getRequestId())
                        .evidenceNumber(evidence.getId().getEvidenceNumber())
                        .mediaUrl(evidence.getMediaUrl())
                        .mediaType(evidence.getMediaType())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public RequestResponseDto cancelRequest(Long requestId) {
        Request request = getOwnedRequest(requestId);

        if (request.getStatus() == Request.RequestStatus.CERRADA ||
                request.getStatus() == Request.RequestStatus.CANCELADA ||
                request.getStatus() == Request.RequestStatus.EXPIRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cancelar una solicitud en estado: " + request.getStatus());
        }

        request.setStatus(Request.RequestStatus.CANCELADA);
        Request updated = requestRepository.save(request);
        return mapToResponseDto(updated);
    }

    @Transactional
    public void transitionToHasProposals(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud especificada no existe"));

        if (request.getStatus() == Request.RequestStatus.PUBLICADA) {
            request.setStatus(Request.RequestStatus.CON_PROPUESTAS);
            requestRepository.save(request);
        }
    }

    @Transactional
    public void closeRequest(Long requestId) {
        Request request = getOwnedRequest(requestId);

        if (request.getStatus() != Request.RequestStatus.CON_PROPUESTAS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden cerrar solicitudes con propuestas activas");
        }

        request.setStatus(Request.RequestStatus.CERRADA);
        requestRepository.save(request);
    }

    @Transactional
    public void expireRequest(Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud especificada no existe"));

        if (request.getStatus() == Request.RequestStatus.PUBLICADA ||
                request.getStatus() == Request.RequestStatus.CON_PROPUESTAS) {
            request.setStatus(Request.RequestStatus.EXPIRADA);
            requestRepository.save(request);
        }
    }

    private Request getOwnedRequest(Long requestId) {
        UUID userId = accountAccess.requireRole(Role.CLIENT);
        Request request = requestRepository.findById(requestId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "La solicitud no existe"));
        accountAccess.requireOwner(userId, request.getClient().getId());
        return request;
    }

    private RequestResponseDto mapToResponseDto(Request request) {
        return RequestResponseDto.builder()
                .id(request.getId())
                .clientId(request.getClient().getId())
                .applianceTypeId(request.getApplianceType().getId())
                .originalDescription(request.getOriginalDescription())
                .brand(request.getBrand())
                .model(request.getModel())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .build();
    }
}