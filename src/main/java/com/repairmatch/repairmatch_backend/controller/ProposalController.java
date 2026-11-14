package com.repairmatch.repairmatch_backend.controller;

import com.repairmatch.repairmatch_backend.dto.ProposalResponseDto;
import com.repairmatch.repairmatch_backend.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/requests/{requestId}/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;

    @org.springframework.web.bind.annotation.PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ProposalResponseDto submit(@PathVariable Long requestId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.repairmatch.repairmatch_backend.dto.CreateProposalDto dto) {
        return proposalService.submit(requestId, dto);
    }

    @GetMapping
    public List<ProposalResponseDto> compareProposals(
            @PathVariable Long requestId
    ) {
        return proposalService.compareByRequest(requestId);
    }
}
