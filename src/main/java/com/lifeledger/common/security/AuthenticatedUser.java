package com.lifeledger.common.security;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.Set;

/**
 * The principal stored in the SecurityContext for every authenticated request.
 * M1's JWT filter (Day 4) creates it from the token's claims.
 *
 * @param id    users.id
 * @param email users.email (also the JPA auditing "created_by" value)
 * @param roles role names without the "ROLE_" prefix, e.g. SELLER, ADMIN
 */
public record AuthenticatedUser(Long id, String email, Set<String> roles) implements AuthenticatedPrincipal {

    public AuthenticatedUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Makes {@code Authentication.getName()} return the email. */
    @Override
    public String getName() {
        return email;
    }
}
