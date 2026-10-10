package org.openelisglobal.security.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

/**
 * Central-login rules for OpenELIS (Nidan backlog KC-38-02/04): link by
 * Keycloak sub only, never by login name; roles only from exact catalog names;
 * admin only from the exact superuser role and only for /app-superusers.
 */
public class OidcUserLinkingTest {

    private static final Set<String> KNOWN = Set.of("Global Administrator", "User Account Administrator", "Reception",
            "Results", "Validation");

    @Test
    public void linkedUserIsReusedOnEveryLogin() {
        OidcUserLinking.Decision d = OidcUserLinking.decide("sub-1", "labtech1", Optional.of("42"), false);
        assertEquals(OidcUserLinking.Action.USE, d.action());
        assertEquals("42", d.systemUserId());
    }

    @Test
    public void newUserIsCreatedWhenNoLocalLoginHasTheName() {
        assertEquals(OidcUserLinking.Action.CREATE,
                OidcUserLinking.decide("sub-2", "labtech2", Optional.empty(), false).action());
    }

    @Test
    public void unlinkedLocalLoginWithSameNameIsRefused() {
        assertEquals(OidcUserLinking.Action.REFUSE,
                OidcUserLinking.decide("sub-3", "admin", Optional.empty(), true).action());
    }

    @Test
    public void missingSubOrUsernameIsRefused() {
        assertEquals(OidcUserLinking.Action.REFUSE, OidcUserLinking.decide("", "x", Optional.empty(), false).action());
        assertEquals(OidcUserLinking.Action.REFUSE,
                OidcUserLinking.decide("sub", " ", Optional.empty(), false).action());
    }

    @Test
    public void rolesAreExactCatalogNamesOnly() {
        Set<String> got = OidcUserLinking.grantedRoles(List.of("Results", "results", "Ghost", "Reception"), List.of(),
                KNOWN);
        assertEquals(Set.of("Results", "Reception"), got);
    }

    @Test
    public void catalogNamesFromThePaddedCharColumnStillMatch() {
        Set<String> got = OidcUserLinking.grantedRoles(List.of("Results", "Validation"), List.of(),
                Set.of("Results         ", "Validation      "));
        assertEquals(Set.of("Results", "Validation"), got);
    }

    @Test
    public void superuserRolesNeedAppSuperusers() {
        Set<String> plain = OidcUserLinking.grantedRoles(List.of("Global Administrator", "Results"),
                List.of("/staff/Lab Tech"), KNOWN);
        assertEquals(Set.of("Results"), plain);
        assertFalse(OidcUserLinking.isAdmin(plain));

        Set<String> su = OidcUserLinking.grantedRoles(List.of("Global Administrator"), List.of("/app-superusers"),
                KNOWN);
        assertTrue(OidcUserLinking.isAdmin(su));
    }

    @Test
    public void adminLookalikesNeverGrantAdmin() {
        Set<String> got = OidcUserLinking.grantedRoles(
                List.of("openelis-admin", "lab-admin", "Global Administrator ", "global administrator"),
                List.of("/app-superusers", "/staff/app-superusers"), KNOWN);
        assertTrue(got.isEmpty());
        assertFalse(OidcUserLinking.isAdmin(Set.of("lab-admin", "openelis-admin")));
    }
}
