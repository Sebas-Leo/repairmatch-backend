package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.CreateEvidenceDto;
import com.repairmatch.repairmatch_backend.dto.CreateRequestDto;
import com.repairmatch.repairmatch_backend.dto.EvidenceResponseDto;
import com.repairmatch.repairmatch_backend.dto.RequestResponseDto;
import com.repairmatch.repairmatch_backend.model.Evidence;
import com.repairmatch.repairmatch_backend.model.EvidenceId;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import com.repairmatch.repairmatch_backend.repository.EvidenceRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final EvidenceRepository evidenceRepository;
    private final UserRepository userRepository;
    private final ApplianceTypeRepository applianceTypeRepository;

    @Transactional
    public RequestResponseDto createRequest(CreateRequestDto dto, UUID currentUserId) {
        User client = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "El usuario autenticado no existe"
                ));

        if (client.getRole() != Role.CLIENT) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo los clientes pueden publicar solicitudes"
            );
        }

        var applianceType = applianceTypeRepository.findById(dto.getApplianceTypeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El tipo de electrodoméstico especificado no existe"
                ));

        Request request = Request.builder()
                .client(client)
                .applianceType(applianceType)
                .originalDescription(dto.getOriginalDescription().strip())
                .brand(stripNullable(dto.getBrand()))
                .model(stripNullable(dto.getModel()))
                .build();

        return mapToResponseDto(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public RequestResponseDto getRequest(Long requestId, UUID currentUserId) {
        return mapToResponseDto(loadOwnedRequest(requestId, currentUserId));
    }

    @Transactional(readOnly = true)
    public List<RequestResponseDto> getOwnRequests(UUID currentUserId) {
        return requestRepository.findByClientIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Transactional
    public EvidenceResponseDto addEvidence(Long requestId, CreateEvidenceDto dto, UUID currentUserId) {
        Request request = loadOwnedRequest(requestId, currentUserId);

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
    public List<EvidenceResponseDto> getEvidencesByRequestId(Long requestId, UUID currentUserId) {
        loadOwnedRequest(requestId, currentUserId);

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
    public RequestResponseDto cancelRequest(Long requestId, UUID currentUserId) {
        Request request = loadOwnedRequest(requestId, currentUserId);

        if (request.getStatus() == Request.RequestStatus.CERRADA ||
                request.getStatus() == Request.RequestStatus.CANCELADA ||
                request.getStatus() == Request.RequestStatus.EXPIRADA) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede cancelar una solicitud en estado: " + request.getStatus()
            );
        }

        request.setStatus(Request.RequestStatus.CANCELADA);
        Request updated = requestRepository.save(request);
        return mapToResponseDto(updated);
    }

    @Transactional
    public void transitionToHasProposals(Long requestId) {
        Request request = loadRequest(requestId);

        if (request.getStatus() == Request.RequestStatus.PUBLICADA) {
            request.setStatus(Request.RequestStatus.CON_PROPUESTAS);
            requestRepository.save(request);
        }
    }

    @Transactional
    public void closeRequest(Long requestId, UUID currentUserId) {
        Request request = loadOwnedRequest(requestId, currentUserId);

        if (request.getStatus() != Request.RequestStatus.CON_PROPUESTAS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden cerrar solicitudes con propuestas activas"
            );
        }

        request.setStatus(Request.RequestStatus.CERRADA);
        requestRepository.save(request);
    }

    @Transactional
    public void expireRequest(Long requestId) {
        Request request = loadRequest(requestId);

        if (request.getStatus() == Request.RequestStatus.PUBLICADA ||
                request.getStatus() == Request.RequestStatus.CON_PROPUESTAS) {
            request.setStatus(Request.RequestStatus.EXPIRADA);
            requestRepository.save(request);
        }
    }

    private RequestResponseDto mapToResponseDto(Request request) {
        return RequestResponseDto.builder()
                .id(request.getId())
                .clientId(request.getClient().getId())
                .applianceTypeId(request.getApplianceType().getId())
                .applianceTypeName(request.getApplianceType().getName())
                .originalDescription(request.getOriginalDescription())
                .brand(request.getBrand())
                .model(request.getModel())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .build();
    }

    private Request loadOwnedRequest(Long requestId, UUID currentUserId) {
        Request request = loadRequest(requestId);
        if (!request.getClient().getId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No tiene autorización para acceder a esta solicitud"
            );
        }
        return request;
    }

    private Request loadRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "La solicitud especificada no existe"
                ));
    }

    private String stripNullable(String value) {
        return value == null ? null : value.strip();
    }
}
