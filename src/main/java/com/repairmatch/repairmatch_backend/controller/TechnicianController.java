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
    private final com.repairmatch.repairmatch_backend.security.AccountAccess accountAccess;
    private final com.repairmatch.repairmatch_backend.service.ServiceService serviceService;

    public TechnicianController(TechnicianService technicianService, com.repairmatch.repairmatch_backend.security.AccountAccess accountAccess, com.repairmatch.repairmatch_backend.service.ServiceService serviceService) {
        this.technicianService = technicianService;
        this.accountAccess = accountAccess; this.serviceService = serviceService;
    }

    @GetMapping("/{id}/reputation")
    public java.util.Map<String, Object> reputation(@PathVariable UUID id) { return serviceService.getTechnicianReputation(id); }

    @GetMapping("/{id}")
    public ResponseEntity<TechnicianProfileResponseDto> getTechnicianById(@PathVariable UUID id) {
        return ResponseEntity.ok(technicianService.getPublicProfile(id));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<TechnicianProfileResponseDto> updateProfile(
            Authentication authentication,
            @Valid @RequestBody TechnicianProfileUpdateRequestDto dto) {
        return ResponseEntity.ok(technicianService.updateProfile(accountAccess.requireRole(com.repairmatch.repairmatch_backend.model.Role.TECHNICIAN), dto));
    }

    @PutMapping("/me/service-areas")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<TechnicianProfileResponseDto> updateServiceAreas(
            Authentication authentication,
            @Valid @RequestBody ServiceAreaUpdateRequestDto dto) {
        return ResponseEntity.ok(technicianService.updateServiceArea(accountAccess.requireRole(com.repairmatch.repairmatch_backend.model.Role.TECHNICIAN), dto));
    }

    @GetMapping("/me/matching-requests")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<List<MatchingRequestResponseDto>> getMatchingRequests(Authentication authentication, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid page or size");
        return ResponseEntity.ok(technicianService.getMatchingRequests(accountAccess.requireRole(com.repairmatch.repairmatch_backend.model.Role.TECHNICIAN)).stream().skip((long) page * size).limit(size).toList());
    }
}