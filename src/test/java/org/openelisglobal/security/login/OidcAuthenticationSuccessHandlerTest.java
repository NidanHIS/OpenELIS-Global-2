package org.openelisglobal.security.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.systemusermodule.service.PermissionModuleService;
import org.openelisglobal.systemusermodule.valueholder.PermissionModule;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.util.ReflectionTestUtils;

/** Central-login success handler (Nidan backlog KC-38-02/04). */
public class OidcAuthenticationSuccessHandlerTest {

    private SystemUserService users;
    private RoleService roles;
    private PermissionModuleService<PermissionModule> perms;
    private OidcAuthenticationSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        users = mock(SystemUserService.class);
        roles = mock(RoleService.class);
        perms = mock(PermissionModuleService.class);
        handler = new OidcAuthenticationSuccessHandler();
        ReflectionTestUtils.setField(handler, "systemUserService", users);
        ReflectionTestUtils.setField(handler, "roleService", roles);
        ReflectionTestUtils.setField(handler, "permissionModuleService", perms);
        when(roles.getAll()).thenReturn(List.of(role("1", "Global Administrator"), role("2", "Results")));
        when(roles.getMatch(any(String.class), any())).thenAnswer(
                inv -> roles.getAll().stream().filter(r -> r.getName().equals(inv.getArgument(1))).findFirst());
        when(perms.getAllPermittedPagesFromAgentId(anyInt())).thenReturn(Set.of("ResultEntry"));
        when(users.getMatch(any(String.class), any())).thenReturn(Optional.empty());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private static Role role(String id, String name) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        return r;
    }

    private static SystemUser user(String id, String login, String externalId) {
        SystemUser u = new SystemUser();
        u.setId(id);
        u.setLoginName(login);
        u.setExternalId(externalId);
        u.setIsActive("Y");
        return u;
    }

    private void login(Map<String, Object> extra) throws Exception {
        Map<String, Object> claims = new java.util.HashMap<>(
                Map.of("sub", "sub-1", "preferred_username", "labtech1", "roles", List.of("Results")));
        claims.putAll(extra);
        OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(300), claims);
        handler.onAuthenticationSuccess(request, response,
                new TestingAuthenticationToken(new DefaultOidcUser(List.of(), token), null));
    }

    private UserSessionData session() {
        return (UserSessionData) request.getSession().getAttribute(IActionConstants.USER_SESSION_DATA);
    }

    @Test
    public void repeatLoginReusesTheLinkedSystemUser() throws Exception {
        when(users.getMatch("externalId", "sub-1")).thenReturn(Optional.of(user("42", "labtech1", "sub-1")));
        login(Map.of());
        assertEquals(42, session().getSystemUserId());
        assertEquals("/Home", response.getRedirectedUrl());
        verify(users, never()).saveCentralLoginUser(any());
    }

    @Test
    public void unlinkedLocalAccountWithSameNameIsRefused() throws Exception {
        when(users.getMatch("loginName", "admin")).thenReturn(Optional.of(user("1", "admin", "1")));
        login(Map.of("preferred_username", "admin", "roles", List.of("Global Administrator"), "groups",
                List.of("/app-superusers")));
        assertNull(session());
        assertEquals("/LoginPage", response.getRedirectedUrl());
        verify(users, never()).saveCentralLoginUser(any());
    }

    @Test
    public void firstLoginCreatesALinkedUser() throws Exception {
        when(users.saveCentralLoginUser(any())).thenAnswer(inv -> {
            SystemUser u = inv.getArgument(0);
            u.setId("77");
            return u;
        });
        login(Map.of("given_name", "Sita", "family_name", "Rai"));
        ArgumentCaptor<SystemUser> saved = ArgumentCaptor.forClass(SystemUser.class);
        verify(users).saveCentralLoginUser(saved.capture());
        assertEquals("sub-1", saved.getValue().getExternalId());
        assertEquals("labtech1", saved.getValue().getLoginName());
        assertEquals(77, session().getSystemUserId());
        assertFalse(session().isAdmin());
    }

    @Test
    public void inactiveLinkedUserIsRefused() throws Exception {
        SystemUser off = user("42", "labtech1", "sub-1");
        off.setIsActive("N");
        when(users.getMatch("externalId", "sub-1")).thenReturn(Optional.of(off));
        login(Map.of());
        assertNull(session());
    }

    @Test
    public void adminOnlyFromExactRoleForAppSuperusers() throws Exception {
        when(users.getMatch("externalId", "sub-1")).thenReturn(Optional.of(user("42", "labtech1", "sub-1")));
        login(Map.of("roles", List.of("Global Administrator"), "groups", List.of("/staff/Lab Tech")));
        assertFalse(session().isAdmin());
        response = new MockHttpServletResponse();
        login(Map.of("roles", List.of("Global Administrator"), "groups", List.of("/app-superusers")));
        assertTrue(session().isAdmin());
    }
}
