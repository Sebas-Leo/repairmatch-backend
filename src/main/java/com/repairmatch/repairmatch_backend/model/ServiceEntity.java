package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Real contracts are kept separate from the legacy prototype services table. */
@Entity
@Table(name = "service_contracts")
@Getter @Setter @NoArgsConstructor
public class ServiceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false, unique = true)
    private Proposal proposal;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private Request request;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ServiceStatus status = ServiceStatus.PROGRAMADO;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    public enum ServiceStatus { PROGRAMADO, EN_ATENCION, COMPLETADO, CANCELADO }
    public Long getProposalId() { return proposal.getId(); }
}
