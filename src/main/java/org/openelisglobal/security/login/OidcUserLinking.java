package org.openelisglobal.security.login;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Central-login rules (Nidan backlog KC-38). Pure, so they are unit-tested
 * without Spring.
 *
 * <ul>
 * <li>A Keycloak user is linked to a system user ONLY by the Keycloak
 * {@code sub}, stored in {@code system_user.external_id}. A first login creates
 * the system user, unless a local account already has that login name: then the
 * login is refused (no account is taken over by name).</li>
 * <li>Roles are the token's {@code roles} that equal a role name exactly.
 * Superuser roles count only for members of {@code /app-superusers}; admin
 * comes only from the exact "Global Administrator" role.</li>
 * </ul>
 */
public final class OidcUserLinking {

    public static final String SUPERUSERS_PATH = "/app-superusers";
    public static final String ADMIN_ROLE = "Global Administrator";
    public static final Set<String> SUPERUSER_ROLES = Set.of(ADMIN_ROLE, "User Account Administrator");

    public enum Action {
        USE, CREATE, REFUSE
    }

    public record Decision(Action action, String systemUserId, String reason) {
    }

    private OidcUserLinking() {
    }

    /**
     * @param sub                          Keycloak subject
     * @param preferredUsername            Keycloak username (login name for a new
     *                                     user)
     * @param linkedSystemUserId           id of the system user whose external id
     *                                     is {@code sub}, if any
     * @param localLoginWithSameNameExists a system user with this login name exists
     *                                     (and is not linked)
     */
    public static Decision decide(String sub, String preferredUsername, Optional<String> linkedSystemUserId,
            boolean localLoginWithSameNameExists) {
        if (isBlank(sub) || isBlank(preferredUsername)) {
            return new Decision(Action.REFUSE, null, "token has no sub or preferred_username");
        }
        if (linkedSystemUserId.isPresent()) {
            return new Decision(Action.USE, linkedSystemUserId.get(), null);
        }
        if (localLoginWithSameNameExists) {
            return new Decision(Action.REFUSE, null,
                    "a local account already has login name " + preferredUsername + " (link it by sub first)");
        }
        return new Decision(Action.CREATE, null, null);
    }

    public static Set<String> grantedRoles(Collection<String> tokenRoles, Collection<String> groups,
            Set<String> knownRoleNames) {
        boolean superuser = groups != null && groups.contains(SUPERUSERS_PATH);
        // system_role.name is a padded CHAR column ("Results "); token roles stay exact
        Set<String> known = new HashSet<>();
        for (String name : knownRoleNames) {
            if (name != null) {
                known.add(name.trim());
            }
        }
        Set<String> out = new HashSet<>();
        if (tokenRoles == null) {
            return out;
        }
        for (String role : tokenRoles) {
            if (role != null && known.contains(role) && (superuser || !SUPERUSER_ROLES.contains(role))) {
                out.add(role);
            }
        }
        return out;
    }

    public static boolean isAdmin(Set<String> grantedRoles) {
        return grantedRoles.contains(ADMIN_ROLE);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
