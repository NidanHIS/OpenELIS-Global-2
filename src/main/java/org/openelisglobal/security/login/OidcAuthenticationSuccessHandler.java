package org.openelisglobal.security.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.validator.BaseErrors;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.systemusermodule.service.PermissionModuleService;
import org.openelisglobal.systemusermodule.valueholder.PermissionModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * Success handler for central login through Keycloak (Nidan backlog KC-38).
 * Rules in {@link OidcUserLinking}: link by Keycloak sub
 * (system_user.external_id) only, never by login name; roles from exact catalog
 * names; admin only from "Global Administrator" for /app-superusers.
 * Persistence goes through the services (transactions live there).
 */
public class OidcAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    static final int SESSION_TIMEOUT_MINUTES = 20;

    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private PermissionModuleService<PermissionModule> permissionModuleService;

    @Value("${org.openelisglobal.timezone:}")
    private String timezone;

    // Where people land after central login. Nidan sets the React frontend
    // (https://<gateway>/openelis/): the legacy /Home page sits outside the
    // gateway's /api/OpenELIS-Global session cookie path.
    @Value("${org.itech.login.oauth.successUrl:/Home}")
    private String successUrl = "/Home";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        if (!(authentication != null && authentication.getPrincipal() instanceof OidcUser)) {
            refuse(request, response, "not an OpenID Connect login", "login.error.sessionsetup");
            return;
        }
        OidcUser oidc = (OidcUser) authentication.getPrincipal();
        String sub = oidc.getSubject();
        String username = oidc.getPreferredUsername();

        Optional<SystemUser> linked = sub == null ? Optional.empty() : systemUserService.getMatch("externalId", sub);
        boolean sameName = linked.isEmpty() && username != null
                && systemUserService.getMatch("loginName", username).isPresent();
        OidcUserLinking.Decision decision = OidcUserLinking.decide(sub, username, linked.map(SystemUser::getId),
                sameName);

        SystemUser systemUser;
        switch (decision.action()) {
        case USE:
            systemUser = linked.get();
            if (!"Y".equals(systemUser.getIsActive())) {
                refuse(request, response, "system user " + systemUser.getId() + " is inactive", "login.error.noOeUser");
                return;
            }
            break;
        case CREATE:
            systemUser = systemUserService.saveCentralLoginUser(newSystemUser(oidc, sub, username));
            break;
        default:
            refuse(request, response, decision.reason(), "login.error.noOeUser");
            return;
        }

        Set<String> known = roleService.getAll().stream().map(Role::getName).collect(Collectors.toSet());
        Set<String> granted = OidcUserLinking.grantedRoles(claimList(oidc, "roles"), claimList(oidc, "groups"), known);

        request.getSession().setMaxInactiveInterval(SESSION_TIMEOUT_MINUTES * 60);
        UserSessionData usd = new UserSessionData();
        usd.setSytemUserId(Integer.parseInt(systemUser.getId()));
        usd.setLoginName(systemUser.getLoginName());
        usd.setElisUserName(systemUser.getLoginName());
        usd.setUserTimeOut(SESSION_TIMEOUT_MINUTES * 60);
        usd.setAdmin(OidcUserLinking.isAdmin(granted));
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, usd);
        request.getSession().setAttribute("timezone", timezone);
        request.getSession().setAttribute(IActionConstants.PERMITTED_ACTIONS_MAP, permittedPages(granted));
        request.getSession().setAttribute("login_method", "oauthLogin");
        LogEvent.logInfo(getClass().getSimpleName(), "onAuthenticationSuccess",
                "central login for " + systemUser.getLoginName() + " with roles " + granted);
        getRedirectStrategy().sendRedirect(request, response, successUrl);
    }

    private SystemUser newSystemUser(OidcUser oidc, String sub, String username) {
        SystemUser user = new SystemUser();
        String first = oidc.getGivenName() == null || oidc.getGivenName().isBlank() ? username : oidc.getGivenName();
        String last = oidc.getFamilyName() == null ? "" : oidc.getFamilyName();
        user.setFirstName(first);
        user.setLastName(last);
        user.setLoginName(username);
        user.setExternalId(sub);
        user.setIsActive("Y");
        user.setIsEmployee("Y");
        user.setInitials(first.substring(0, 1) + (last.isEmpty() ? "" : last.substring(0, 1)));
        user.setSysUserId("1");
        return user;
    }

    private Set<String> permittedPages(Set<String> roleNames) {
        Set<String> pages = new HashSet<>();
        for (String name : roleNames) {
            roleService.getMatch("name", name).ifPresent(role -> pages
                    .addAll(permissionModuleService.getAllPermittedPagesFromAgentId(Integer.parseInt(role.getId()))));
        }
        return pages;
    }

    private static List<String> claimList(OidcUser oidc, String name) {
        Object value = oidc.getClaims().get(name);
        if (value instanceof Collection<?> c) {
            return c.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return value instanceof String s ? List.of(s) : List.of();
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, String why, String errorKey)
            throws IOException {
        LogEvent.logWarn(getClass().getSimpleName(), "onAuthenticationSuccess", "central login refused: " + why);
        SecurityContextHolder.getContext().setAuthentication(null);
        BaseErrors errors = new BaseErrors();
        errors.reject(errorKey);
        request.getSession().setAttribute(Constants.LOGIN_ERRORS, errors);
        getRedirectStrategy().sendRedirect(request, response, "/LoginPage");
    }
}
