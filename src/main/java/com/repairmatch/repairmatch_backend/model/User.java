package com.repairmatch.repairmatch_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(
        name = "uk_users_email", columnNames = "email"
))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * Identity verification state. Only the status, the timestamp and the provider session id are kept:
     * never document images, document numbers or biometrics. The column is nullable so existing rows
     * need no backfill (null reads as NOT_STARTED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", length = 20)
    private IdentityStatus identityStatus;

    @Column(name = "identity_verified_at")
    private LocalDateTime identityVerifiedAt;

    /**
     * Whether the profile name matched the name on the identity document. Only this flag is kept, never the
     * document name. Nullable: null when not verified or when the provider sent no name.
     */
    @Column(name = "identity_name_matches")
    private Boolean identityNameMatches;

    @Column(name = "identity_session_id", length = 64)
    private String identitySessionId;

    public User(UUID id, String name, String email, String passwordHash, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public IdentityStatus getIdentityStatus() {
        return identityStatus == null ? IdentityStatus.NOT_STARTED : identityStatus;
    }

    public boolean isIdentityVerified() {
        return getIdentityStatus() == IdentityStatus.VERIFIED;
    }

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }
}
