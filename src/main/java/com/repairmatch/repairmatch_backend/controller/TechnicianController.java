package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.*;
import com.repairmatch.repairmatch_backend.service.ProposalService;
import com.repairmatch.repairmatch_backend.service.TechnicianService;
import jakarta.validation.Valid;
import com.repairmatch.repairmatch_backend.model.Role;
import org.springframework.data.domain.Sort;
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
    private final ProposalService proposalService;

    public TechnicianController(TechnicianService technicianService, com.repairmatch.repairmatch_backend.security.AccountAccess accountAccess, com.repairmatch.repairmatch_backend.service.ServiceService serviceService, ProposalService proposalService) {
        this.technicianService = technicianService;
        this.accountAccess = accountAccess; this.serviceService = serviceService; this.proposalService = proposalService;
    }

    @GetMapping("/{id}/reputation")
    public java.util.Map<String, Object> reputation(@PathVariable UUID id) { return serviceService.getTechnicianReputation(id); }

    @GetMapping("/{id}/reviews")
    public List<ReviewResponseDto> reviews(@PathVariable UUID id) { return serviceService.getTechnicianReviews(id); }

    @GetMapping("/{id}")
    public ResponseEntity<TechnicianProfileResponseDto> getTechnicianById(@PathVariable UUID id) {
        return ResponseEntity.ok(technicianService.getPublicProfile(id));
    }

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public ResponseEntity<TechnicianProfileResponseDto> getOwnProfile() {
        return ResponseEntity.ok(technicianService.getOwnProfile(accountAccess.requireRole(com.repairmatch.repairmatch_backend.model.Role.TECHNICIAN)));
    }

    @GetMapping("/me/proposals")
    @PreAuthorize("hasAuthority('ROLE_TECHNICIAN') or hasRole('TECHNICIAN')")
    public PageResponse<ProposalResponseDto> getOwnProposals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        var order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return proposalService.listOwn(PageResponse.request(page, size, order));
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
    public ResponseEntity<PageResponse<MatchingRequestResponseDto>> getMatchingRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        // Distance filtering happens in memory, so the full result is computed and then sliced.
        var window = PageResponse.request(page, size, Sort.unsorted());
        var all = technicianService.getMatchingRequests(accountAccess.requireRole(Role.TECHNICIAN));
        return ResponseEntity.ok(PageResponse.slice(all, window));
    }
}
