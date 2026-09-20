package com.repairmatch.repairmatch_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvidenceResponseDto {
    private Long requestId;
    private Integer evidenceNumber;
    private String mediaUrl;
    private String mediaType;
}