package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_reviews")
@Getter @Setter @NoArgsConstructor
public class ReviewEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, unique = true)
    private ServiceEntity service;
    @Column(nullable = false)
    private Integer rating;
    @Column(length = 2000, nullable = false)
    private String comment;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
