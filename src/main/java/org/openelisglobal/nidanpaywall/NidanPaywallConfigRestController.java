package org.openelisglobal.nidanpaywall;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
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
 *   "allowOpd": false,
 *   "allowIpd": true,
 *   "allowEr":  true
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
    public ResponseEntity<PaywallConfigDto> getConfig() {
        Set<String> allowed = configService.getAllowedVisitTypes();
        PaywallConfigDto dto = new PaywallConfigDto(allowed.contains("OPD"), allowed.contains("IPD"),
                allowed.contains("ER"));
        return ResponseEntity.ok(dto);
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ROLE_PAYWALL_ADMINISTRATION')")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaywallConfigDto> saveConfig(@RequestBody PaywallConfigDto dto) {
        if (dto == null) {
            return ResponseEntity.badRequest().build();
        }
        configService.saveConfig(dto.isAllowOpd(), dto.isAllowIpd(), dto.isAllowEr());
        // Return the persisted state so the UI can confirm what was saved.
        return getConfig();
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    public static class PaywallConfigDto {

        @JsonProperty("allowOpd")
        private boolean allowOpd;

        @JsonProperty("allowIpd")
        private boolean allowIpd;

        @JsonProperty("allowEr")
        private boolean allowEr;

        /** Jackson deserialization constructor. */
        public PaywallConfigDto() {
        }

        public PaywallConfigDto(boolean allowOpd, boolean allowIpd, boolean allowEr) {
            this.allowOpd = allowOpd;
            this.allowIpd = allowIpd;
            this.allowEr = allowEr;
        }

        public boolean isAllowOpd() {
            return allowOpd;
        }

        public void setAllowOpd(boolean allowOpd) {
            this.allowOpd = allowOpd;
        }

        public boolean isAllowIpd() {
            return allowIpd;
        }

        public void setAllowIpd(boolean allowIpd) {
            this.allowIpd = allowIpd;
        }

        public boolean isAllowEr() {
            return allowEr;
        }

        public void setAllowEr(boolean allowEr) {
            this.allowEr = allowEr;
        }
    }
}
