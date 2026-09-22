package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.CreateEvidenceDto;
import com.repairmatch.repairmatch_backend.dto.EvidenceResponseDto;
import com.repairmatch.repairmatch_backend.dto.RequestResponseDto;
import com.repairmatch.repairmatch_backend.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping("/{requestId}/evidences")
    public ResponseEntity<EvidenceResponseDto> addEvidence(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateEvidenceDto dto) {

        EvidenceResponseDto response = requestService.addEvidence(requestId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{requestId}/evidences")
    public ResponseEntity<List<EvidenceResponseDto>> getEvidencesByRequestId(@PathVariable Long requestId) {
        List<EvidenceResponseDto> response = requestService.getEvidencesByRequestId(requestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<RequestResponseDto> cancelRequest(
            @PathVariable Long requestId) {

        RequestResponseDto response = requestService.cancelRequest(requestId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/close")
    public ResponseEntity<Void> closeRequest(
            @PathVariable Long requestId) {

        requestService.closeRequest(requestId);
        return ResponseEntity.noContent().build();
    }
}