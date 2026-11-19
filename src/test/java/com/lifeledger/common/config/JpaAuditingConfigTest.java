package com.lifeledger.common.config;

import com.lifeledger.common.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    private final AuditorAware<String> auditorAware = new JpaAuditingConfig().auditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditor_whenNobodyLoggedIn_isSystem() {
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void auditor_whenLoggedIn_isUserName() {
        // Given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("buyer@example.com", null,
                        AuthorityUtils.createAuthorityList("ROLE_BUYER")));

        // When / Then
        assertThat(auditorAware.getCurrentAuditor()).contains("buyer@example.com");
    }

    @Test
    void auditor_withJwtPrincipal_isEmailNotToString() {
        // Given: the principal type the JWT filter will set
        var user = new AuthenticatedUser(7L, "seller@example.com", Set.of("SELLER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        AuthorityUtils.createAuthorityList("ROLE_SELLER")));

        // When / Then
        assertThat(auditorAware.getCurrentAuditor()).contains("seller@example.com");
    }
}
