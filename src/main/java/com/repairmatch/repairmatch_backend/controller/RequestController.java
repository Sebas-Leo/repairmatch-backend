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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {
    @PatchMapping("/{requestId}/location")
    public com.repairmatch.repairmatch_backend.dto.RequestResponseDto updateLocation(@PathVariable Long requestId, @jakarta.validation.Valid @RequestBody com.repairmatch.repairmatch_backend.dto.RequestLocationDto dto) {
        return requestService.updateLocation(requestId, dto);
    }

    private final RequestService requestService;

    @PostMapping
    public ResponseEntity<RequestResponseDto> createRequest(
            @Valid @RequestBody CreateRequestDto dto
    ) {
        RequestResponseDto response = requestService.createRequest(
                dto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<RequestResponseDto> getOwnRequests() {
        return requestService.getOwnRequests();
    }

    @GetMapping("/{requestId}")
    public RequestResponseDto getRequest(
            @PathVariable Long requestId
    ) {
        return requestService.getRequest(requestId);
    }

    @PostMapping("/{requestId}/evidences")
    public ResponseEntity<EvidenceResponseDto> addEvidence(
            @PathVariable Long requestId,
            @Valid @RequestBody CreateEvidenceDto dto) {

        EvidenceResponseDto response = requestService.addEvidence(
                requestId,
                dto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{requestId}/evidences")
    public ResponseEntity<List<EvidenceResponseDto>> getEvidencesByRequestId(
            @PathVariable Long requestId
    ) {
        List<EvidenceResponseDto> response = requestService.getEvidencesByRequestId(
                requestId
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<RequestResponseDto> cancelRequest(
            @PathVariable Long requestId) {

        RequestResponseDto response = requestService.cancelRequest(
                requestId
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{requestId}/close")
    public ResponseEntity<Void> closeRequest(
            @PathVariable Long requestId) {

        requestService.closeRequest(requestId);
        return ResponseEntity.noContent().build();
    }

}
