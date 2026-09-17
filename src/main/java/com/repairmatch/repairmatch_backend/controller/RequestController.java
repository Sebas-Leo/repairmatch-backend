package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.CreateEvidenceDto;
import com.repairmatch.repairmatch_backend.dto.EvidenceResponseDto;
import com.repairmatch.repairmatch_backend.dto.RequestResponseDto;
import com.repairmatch.repairmatch_backend.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping("/{requestId}/evidences")
    public ResponseEntity<EvidenceResponseDto> addEvidence(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateEvidenceDto dto,
            Authentication authentication) {

        UUID currentUserId = UUID.fromString(authentication.getName());
        EvidenceResponseDto response = requestService.addEvidence(requestId, dto, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{requestId}/evidences")
    public ResponseEntity<List<EvidenceResponseDto>> getEvidencesByRequestId(@PathVariable Long requestId) {
        List<EvidenceResponseDto> response = requestService.getEvidencesByRequestId(requestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<RequestResponseDto> cancelRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        UUID currentUserId = UUID.fromString(authentication.getName());
        RequestResponseDto response = requestService.cancelRequest(requestId, currentUserId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/close")
    public ResponseEntity<Void> closeRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        UUID currentUserId = UUID.fromString(authentication.getName());
        requestService.closeRequest(requestId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}