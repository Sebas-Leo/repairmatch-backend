package com.repairmatch.repairmatch_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ProposalResponseDto {
    private Long id;
    private Long requestId;
    private UUID technicianId;
    private String technicianName;
    private BigDecimal diagnosticCost;
    private LocalDateTime availableAt;
    private String conditions;
    private LocalDateTime createdAt;
    private String status;
}
