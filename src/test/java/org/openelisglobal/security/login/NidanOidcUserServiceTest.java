package org.openelisglobal.security.login;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * OIDC logins carry the same ROLE_* authorities as password logins (Nidan
 * backlog KC-38-06).
 */
public class NidanOidcUserServiceTest {

    private static final Set<String> KNOWN = Set.of("Global Administrator", "Paywall Administration", "Results");

    private static Set<String> names(List<String> roles, List<String> groups) {
        return NidanOidcUserService.authoritiesFor(roles, groups, KNOWN).stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).collect(Collectors.toSet());
    }

    @Test
    public void sessionRolesComeFromOegAuthoritiesForAllLabUnits() {
        Set<String> oeg = NidanOidcUserService.authoritiesFor(List.of("Results", "Ghost"), List.of(), KNOWN).stream()
                .map(a -> a.getAuthority()).filter(a -> a.startsWith("oeg-")).collect(Collectors.toSet());
        assertEquals(Set.of("oeg-Results-AllLabUnits"), oeg);
    }

    @Test
    public void mappedRoleGetsItsAuthority() {
        assertEquals(Set.of("ROLE_PAYWALL_ADMINISTRATION", "ROLE_RESULTS"),
                names(List.of("Paywall Administration", "Results"), List.of()));
    }

    @Test
    public void labTechWithoutTheRoleGetsNoPaywallAuthority() {
        assertEquals(Set.of("ROLE_RESULTS"), names(List.of("Results", "paywall administration"), List.of()));
    }

    @Test
    public void adminAuthoritiesOnlyForAppSuperusers() {
        assertEquals(Set.of(), names(List.of("Global Administrator"), List.of("/staff/Lab Tech")));
        assertEquals(Set.of("ROLE_GLOBAL_ADMIN", "ROLE_ADMIN"),
                names(List.of("Global Administrator"), List.of("/app-superusers")));
    }
}
