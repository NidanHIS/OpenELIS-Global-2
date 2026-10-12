package org.openelisglobal.nidanrolecatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.security.login.OidcUserLinking;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only list of this OpenELIS's system roles for Keycloak's role sync
 * (nidan-docker keycloak/role-sync): Keycloak hands OpenELIS roles by exact
 * system_role name (OidcUserLinking).
 *
 * <p>
 * Open page (SecurityConfig OPEN_PAGES) guarded by its own shared secret
 * (NIDAN_ROLE_SYNC_SECRET, header X-Nidan-Role-Sync-Secret), compared in
 * constant time; off (404) when the secret is unset or shorter than 32. The
 * gateway denies this path, so only the internal network reaches it.
 */
@RestController
public class NidanRoleCatalogRestController {

    static final String PATH = "/rest/nidan/role-catalog";

    @Autowired
    private RoleService roleService;

    @Value("${NIDAN_ROLE_SYNC_SECRET:}")
    private String secret;

    @GetMapping(value = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> roles(@RequestHeader(value = "X-Nidan-Role-Sync-Secret", required = false) String given) {
        if (secret == null || secret.length() < 32) {
            return ResponseEntity.notFound().build();
        }
        if (given == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                given.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // system_role.name is a padded CHAR column ("Results "): tokens carry the
        // trimmed name (OidcUserLinking)
        List<Map<String, Object>> roles = roleService.getAllActiveRoles().stream().filter(r -> r.getName() != null)
                .map(r -> Map.<String, Object>of("name", r.getName().trim(), "description",
                        r.getDescription() == null ? "" : r.getDescription().trim(), "superuser",
                        OidcUserLinking.SUPERUSER_ROLES.contains(r.getName().trim())))
                .sorted(Comparator.comparing(m -> (String) m.get("name"))).toList();
        return ResponseEntity.ok(Map.of("app", "openelis", "roles", roles));
    }
}
