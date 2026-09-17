package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.CreateEvidenceDto;
import com.repairmatch.repairmatch_backend.dto.EvidenceResponseDto;
import com.repairmatch.repairmatch_backend.model.Evidence;
import com.repairmatch.repairmatch_backend.model.EvidenceId;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.repository.EvidenceRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public EvidenceResponseDto addEvidence(Long requestId, CreateEvidenceDto dto, UUID currentUserId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud especificada no existe"));

        if (!request.getClient().getId().equals(currentUserId)) {
            throw new SecurityException("No tiene autorización para adjuntar evidencias a esta solicitud");
        }

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
        if (!requestRepository.existsById(requestId)) {
            throw new IllegalArgumentException("La solicitud especificada no existe");
        }

        return evidenceRepository.findByRequestId(requestId).stream()
                .map(evidence -> EvidenceResponseDto.builder()
                        .requestId(evidence.getId().getRequestId())
                        .evidenceNumber(evidence.getId().getEvidenceNumber())
                        .mediaUrl(evidence.getMediaUrl())
                        .mediaType(evidence.getMediaType())
                        .build())
                .collect(Collectors.toList());
    }
}