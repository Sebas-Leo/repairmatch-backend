package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.*;
import com.repairmatch.repairmatch_backend.service.TechnicianService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final TechnicianService technicianService;

    public TechnicianController(TechnicianService technicianService) {
        this.technicianService = technicianService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TechnicianProfileResponseDto> getTechnicianById(@PathVariable UUID id) {
        return ResponseEntity.ok(technicianService.getPublicProfile(id));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<TechnicianProfileResponseDto> updateProfile(
            Authentication authentication,
            @Valid @RequestBody TechnicianProfileUpdateRequestDto dto) {
        return ResponseEntity.ok(technicianService.updateProfile(authentication.getName(), dto));
    }

    @PutMapping("/me/service-areas")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<TechnicianProfileResponseDto> updateServiceAreas(
            Authentication authentication,
            @Valid @RequestBody ServiceAreaUpdateRequestDto dto) {
        return ResponseEntity.ok(technicianService.updateServiceArea(authentication.getName(), dto));
    }

    @GetMapping("/me/matching-requests")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<List<MatchingRequestResponseDto>> getMatchingRequests(Authentication authentication) {
        return ResponseEntity.ok(technicianService.getMatchingRequests(authentication.getName()));
    }
}