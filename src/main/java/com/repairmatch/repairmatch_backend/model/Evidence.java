package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "evidences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evidence {

    @EmbeddedId
    private EvidenceID id;

    @MapsId("requestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(nullable = false, length = 500)
    private String mediaUrl;

    @Column(length = 50)
    private String mediaType;
}