package com.repairmatch.repairmatch_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RequestResponseDto {
    private Long id;
    private Long clientId;
    private Long applianceTypeId;
    private String applianceTypeName;
    private String originalDescription;
    private String brand;
    private String model;
    private String status;
    private LocalDateTime createdAt;
}
