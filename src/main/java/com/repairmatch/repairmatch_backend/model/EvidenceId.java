package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EvidenceId implements Serializable {

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "evidence_number")
    private Integer evidenceNumber;
}