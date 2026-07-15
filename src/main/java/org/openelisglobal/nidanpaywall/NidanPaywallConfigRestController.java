package org.openelisglobal.nidanpaywall;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST API for the paywall visit-type bypass configuration.
 *
 * <p>
 * GET /rest/nidan/paywall-config — returns current flags (PAYWALL_ADMIN only)
 * PUT /rest/nidan/paywall-config — saves updated flags (PAYWALL_ADMIN only)
 *
 * <p>
 * Both endpoints require the {@code Paywall Administration} role enforced via
 * {@code @PreAuthorize}. Spring Method Security is enabled globally via
 * {@code @EnableMethodSecurity(prePostEnabled = true)} on
 * {@code SecurityConfig}.
 *
 * <p>
 * Response / request body shape:
 * 
 * <pre>
 * {
 *   "OPD Visit": false,
 *   "IPD Visit": true,
 *   "ER Visit":  true
 * }
 * </pre>
 */
@RestController
@RequestMapping("/rest/nidan/paywall-config")
public class NidanPaywallConfigRestController {

    @Autowired
    private NidanPaywallConfigService configService;

    // ── GET ───────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ROLE_PAYWALL_ADMINISTRATION')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Boolean>> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ROLE_PAYWALL_ADMINISTRATION')")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Boolean>> saveConfig(@RequestBody Map<String, Boolean> config) {
        if (config == null) {
            return ResponseEntity.badRequest().build();
        }
        configService.updateConfig(config);
        // Return the persisted state so the UI can confirm what was saved.
        return getConfig();
    }
}
