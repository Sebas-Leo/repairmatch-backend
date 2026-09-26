package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "proposals",
        indexes = @Index(name = "idx_proposals_request", columnList = "request_id"),
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"request_id", "technician_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private User technician;

    @Column(name = "diagnostic_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal diagnosticCost;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(nullable = false, length = 1000)
    private String conditions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Proposal(
            Request request,
            User technician,
            BigDecimal diagnosticCost,
            LocalDateTime availableAt,
            String conditions
    ) {
        this.request = request;
        this.technician = technician;
        this.diagnosticCost = diagnosticCost;
        this.availableAt = availableAt;
        this.conditions = conditions;
    }

    public enum Status { PENDING, ACCEPTED, REJECTED }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    public void setStatus(Status status) { this.status = status; }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
