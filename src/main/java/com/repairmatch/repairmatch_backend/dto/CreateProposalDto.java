package com.repairmatch.repairmatch_backend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProposalDto(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal diagnosticCost,
        @NotNull @Future LocalDateTime availableAt,
        @NotBlank @Size(max = 1000) String conditions) {}
