package com.repairmatch.repairmatch_backend.security;

import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.exception.*;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Shared identity checks. Resource owners must come from persisted data. */
@Component
@RequiredArgsConstructor
public class AccountAccess {
    private final UserRepository userRepository;

    public UUID requireRole(Role requiredRole) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            throw new AuthenticationRequiredException( "Autenticación requerida");
        }
        UUID id;
        try {
            id = UUID.fromString(token.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new AuthenticationRequiredException( "Identidad inválida");
        }
        User user = userRepository.findById(id).orElseThrow(() ->
                new AuthenticationRequiredException( "Cuenta no disponible"));
        boolean tokenAllowsRole = token.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + requiredRole.name()));
        if (user.getRole() != requiredRole || !tokenAllowsRole) {
            throw new ForbiddenOperationException( "Rol no autorizado");
        }
        return id;
    }

    public void requireOwner(UUID authenticatedId, UUID persistedOwnerId) {
        if (!authenticatedId.equals(persistedOwnerId)) {
            throw new ForbiddenOperationException( "No tiene acceso a este recurso");
        }
    }
}
