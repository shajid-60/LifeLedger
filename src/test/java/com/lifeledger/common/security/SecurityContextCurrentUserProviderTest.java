package com.lifeledger.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserId_whenLoggedIn_returnsPrincipalId() {
        // Given
        logIn(new AuthenticatedUser(7L, "seller@example.com", Set.of("SELLER")));

        // When / Then
        assertThat(provider.currentUserId()).isEqualTo(7L);
        assertThat(provider.currentUser().email()).isEqualTo("seller@example.com");
    }

    @Test
    void currentUserId_whenNobodyLoggedIn_throwsAuthenticationCredentialsNotFound() {
        assertThatThrownBy(provider::currentUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void hasRole_matchesOnlyGrantedRoles() {
        // Given
        logIn(new AuthenticatedUser(1L, "admin@example.com", Set.of("ADMIN")));

        // When / Then
        assertThat(provider.hasRole("ADMIN")).isTrue();
        assertThat(provider.hasRole("SELLER")).isFalse();
    }

    @Test
    void hasRole_whenNobodyLoggedIn_returnsFalse() {
        assertThat(provider.hasRole("ADMIN")).isFalse();
    }

    private static void logIn(AuthenticatedUser user) {
        var authorities = user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.copyOf(authorities)));
    }
}
