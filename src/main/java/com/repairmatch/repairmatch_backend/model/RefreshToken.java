package com.repairmatch.repairmatch_backend.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="refresh_tokens", uniqueConstraints=@UniqueConstraint(name="uk_refresh_token_hash",columnNames="token_hash"))
@Getter @Setter @NoArgsConstructor
public class RefreshToken {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private boolean revoked;
}
