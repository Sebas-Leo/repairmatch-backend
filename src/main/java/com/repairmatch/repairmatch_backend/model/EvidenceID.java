package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EvidenceID implements Serializable {
    private Long requestId;
    private Integer evidenceNumber;
}