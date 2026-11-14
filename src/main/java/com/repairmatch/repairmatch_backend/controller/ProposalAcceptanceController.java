package com.repairmatch.repairmatch_backend.controller;
import com.repairmatch.repairmatch_backend.dto.ServiceResponseDTO;
import com.repairmatch.repairmatch_backend.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/proposals") @RequiredArgsConstructor
public class ProposalAcceptanceController {
    private final ProposalService proposalService;
    @PostMapping("/{id}/accept") @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponseDTO accept(@PathVariable Long id) { return proposalService.accept(id); }
}
