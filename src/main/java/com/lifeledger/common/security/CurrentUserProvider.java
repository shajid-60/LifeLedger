package com.lifeledger.common.security;

/**
 * Cross-module contract (M1 → M2, M3): who is making the current request.
 * Inject this instead of reading the SecurityContext directly, so services stay
 * easy to unit-test (mock this interface).
 */
public interface CurrentUserProvider {

    /**
     * @return the logged-in user's id
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *         if nobody is logged in (mapped to 401)
     */
    Long currentUserId();

    /**
     * @return the full principal (id, email, roles)
     */
    AuthenticatedUser currentUser();

    /**
     * @param role role name without the "ROLE_" prefix, e.g. "ADMIN"
     */
    boolean hasRole(String role);
}
