package org.openelisglobal.security.login;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Gives a central-login user the same ROLE_* authorities a password login would
 * have (Nidan backlog KC-38-06), so {@code @PreAuthorize} checks such as
 * ROLE_PAYWALL_ADMINISTRATION work. Roles are granted by
 * {@link OidcUserLinking#grantedRoles}: exact role names only, superuser roles
 * only for /app-superusers.
 */
public class NidanOidcUserService extends OidcUserService {

    @Autowired
    private RoleService roleService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(userRequest);
        Set<String> known = roleService.getAll().stream().map(Role::getName).collect(Collectors.toSet());
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(
                authoritiesFor(list(user.getClaims().get("roles")), list(user.getClaims().get("groups")), known));
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), "preferred_username");
    }

    static Set<GrantedAuthority> authoritiesFor(Collection<String> tokenRoles, Collection<String> groups,
            Set<String> knownRoleNames) {
        Set<String> granted = OidcUserLinking.grantedRoles(tokenRoles, groups, knownRoleNames);
        Set<GrantedAuthority> out = new LinkedHashSet<>();
        for (String role : granted) {
            out.add(new SimpleGrantedAuthority(CustomUserDetailsService.toRoleAuthority(role)));
            // /session reads an SSO user's roles from "oeg-<role>-<lab unit>" authorities
            // (the SAML
            // convention); the React UI gates its pages on them. Central roles are not
            // unit-scoped.
            out.add(new SimpleGrantedAuthority("oeg-" + role + "-AllLabUnits"));
        }
        if (OidcUserLinking.isAdmin(granted)) {
            out.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return out;
    }

    private static List<String> list(Object value) {
        if (value instanceof Collection<?> c) {
            return c.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return value instanceof String s ? List.of(s) : List.of();
    }
}
