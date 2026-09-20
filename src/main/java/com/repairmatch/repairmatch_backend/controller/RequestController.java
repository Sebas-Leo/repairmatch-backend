package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.CreateEvidenceDto;
import com.repairmatch.repairmatch_backend.dto.CreateRequestDto;
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

    @PostMapping
    public ResponseEntity<RequestResponseDto> createRequest(
            @Valid @RequestBody CreateRequestDto dto,
            Authentication authentication
    ) {
        RequestResponseDto response = requestService.createRequest(
                dto,
                currentUserId(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<RequestResponseDto> getOwnRequests(Authentication authentication) {
        return requestService.getOwnRequests(currentUserId(authentication));
    }

    @GetMapping("/{requestId}")
    public RequestResponseDto getRequest(
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        return requestService.getRequest(requestId, currentUserId(authentication));
    }

    @PostMapping("/{requestId}/evidences")
    public ResponseEntity<EvidenceResponseDto> addEvidence(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateEvidenceDto dto,
            Authentication authentication) {

        EvidenceResponseDto response = requestService.addEvidence(
                requestId,
                dto,
                currentUserId(authentication)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{requestId}/evidences")
    public ResponseEntity<List<EvidenceResponseDto>> getEvidencesByRequestId(
            @PathVariable Long requestId,
            Authentication authentication
    ) {
        List<EvidenceResponseDto> response = requestService.getEvidencesByRequestId(
                requestId,
                currentUserId(authentication)
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<RequestResponseDto> cancelRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        RequestResponseDto response = requestService.cancelRequest(
                requestId,
                currentUserId(authentication)
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/close")
    public ResponseEntity<Void> closeRequest(
            @PathVariable Long requestId,
            Authentication authentication) {

        requestService.closeRequest(requestId, currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
