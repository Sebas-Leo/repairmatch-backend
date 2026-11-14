package com.repairmatch.repairmatch_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RequestResponseDto {
    private Double latitude;
    private Double longitude;
    private Long id;
    private UUID clientId;
    private Long applianceTypeId;
    private String applianceTypeName;
    private String originalDescription;
    private String brand;
    private String model;
    private String status;
    private LocalDateTime createdAt;
}