package org.openelisglobal.nidanrolecatalog;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class NidanRoleCatalogRestControllerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    private RoleService roleService;

    @InjectMocks
    private NidanRoleCatalogRestController controller;

    private static Role role(String name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    @Test
    public void roles_shouldBeOffWithoutALongEnoughSecret() {
        ReflectionTestUtils.setField(controller, "secret", "");
        assertEquals(404, controller.roles(SECRET).getStatusCode().value());
        ReflectionTestUtils.setField(controller, "secret", "short");
        assertEquals(404, controller.roles("short").getStatusCode().value());
    }

    @Test
    public void roles_shouldRefuseAWrongOrMissingSecret() {
        ReflectionTestUtils.setField(controller, "secret", SECRET);
        assertEquals(401, controller.roles(null).getStatusCode().value());
        assertEquals(401, controller.roles(SECRET + "x").getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void roles_shouldListActiveRolesAndFlagTheSuperuserOnes() {
        ReflectionTestUtils.setField(controller, "secret", SECRET);
        when(roleService.getAllActiveRoles())
                .thenReturn(Arrays.asList(role("Results   "), role("Global Administrator  ")));
        ResponseEntity<?> res = controller.roles(SECRET);
        assertEquals(200, res.getStatusCode().value());
        List<Map<String, Object>> roles = (List<Map<String, Object>>) ((Map<String, Object>) res.getBody())
                .get("roles");
        assertEquals("Global Administrator", roles.get(0).get("name"));
        assertEquals(true, roles.get(0).get("superuser"));
        assertEquals("Results", roles.get(1).get("name"));
        assertEquals(false, roles.get(1).get("superuser"));
    }
}
