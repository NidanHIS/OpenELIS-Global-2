package org.openelisglobal.nidanpaywall;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads the three user-configurable paywall bypass flags from
 * {@code site_information} and exposes a simple set of allowed visit-type
 * tokens.
 *
 * <p>
 * Canonical token values (upper-case, no whitespace):
 * <ul>
 * <li>{@code "OPD"} — controlled by {@code nidan_paywall_allow_opd}</li>
 * <li>{@code "IPD"} — controlled by {@code nidan_paywall_allow_ipd}</li>
 * <li>{@code "ER"} — controlled by {@code nidan_paywall_allow_er}</li>
 * </ul>
 *
 * <p>
 * Values are read from the DB on every call — no in-process cache — so an admin
 * change takes effect on the very next paywall-check request without requiring
 * a restart. The three DB reads are tiny indexed lookups; the added latency is
 * negligible compared to the Odoo HTTP call that follows.
 *
 * <p>
 * If a row is missing (e.g. fresh install before Liquibase has run), the flag
 * defaults to {@code false} — paywall enforced — which is the safe direction.
 */
@Service
public class NidanPaywallConfigService {

    private static final String KEY_OPD = "nidan_paywall_allow_opd";
    private static final String KEY_IPD = "nidan_paywall_allow_ipd";
    private static final String KEY_ER = "nidan_paywall_allow_er";

    /**
     * Canonical OpenMRS visit-type names, lower-cased for comparison. Source of
     * truth: the visit_type table in the OpenMRS instance.
     *
     * OPD → "OPD Visit" IPD → "Inpatient Visit" ER → "Emergency Visit"
     */
    private static final String CANONICAL_OPD = "opd visit";
    private static final String CANONICAL_IPD = "inpatient visit";
    private static final String CANONICAL_ER = "emergency visit";

    @Autowired
    private SiteInformationService siteInformationService;

    /**
     * Returns an immutable set of upper-cased visit-type tokens that are currently
     * configured to bypass the paywall. The set is built fresh on every call.
     *
     * @return never null; may be empty if all flags are false
     */
    public Set<String> getAllowedVisitTypes() {
        Set<String> allowed = new HashSet<>();
        if (isEnabled(KEY_OPD))
            allowed.add("OPD");
        if (isEnabled(KEY_IPD))
            allowed.add("IPD");
        if (isEnabled(KEY_ER))
            allowed.add("ER");
        return Collections.unmodifiableSet(allowed);
    }

    /**
     * Convenience method: true when the given visit type (case-insensitive,
     * trimmed) maps to an allowed canonical OpenMRS visit-type name.
     *
     * <p>
     * Canonical name → config token mapping:
     * <ul>
     * <li>"OPD Visit" → OPD → {@code nidan_paywall_allow_opd}</li>
     * <li>"Inpatient Visit" → IPD → {@code nidan_paywall_allow_ipd}</li>
     * <li>"Emergency Visit" → ER → {@code nidan_paywall_allow_er}</li>
     * </ul>
     *
     * <p>
     * Any other value (e.g. "Lab Visit", "Group Session", null) returns
     * {@code false} — paywall is enforced.
     *
     * @param visitType raw value from {@code IncomingOrder.visitType}; may be null
     * @return true → bypass paywall; false → proceed to Odoo check
     */
    public boolean isVisitTypeAllowed(String visitType) {
        if (visitType == null || visitType.trim().isEmpty()) {
            return false;
        }
        String normalised = visitType.trim().toLowerCase();

        if (CANONICAL_OPD.equals(normalised))
            return isEnabled(KEY_OPD);
        if (CANONICAL_IPD.equals(normalised))
            return isEnabled(KEY_IPD);
        if (CANONICAL_ER.equals(normalised))
            return isEnabled(KEY_ER);

        // Anything else (Lab Visit, Group Session, …) — never bypass.
        return false;
    }

    // ── Read helpers ─────────────────────────────────────────────────────────

    /**
     * Returns the current boolean value for a site_information row. Null row or
     * non-"true" value → false.
     */
    private boolean isEnabled(String key) {
        SiteInformation si = siteInformationService.getSiteInformationByName(key);
        return si != null && "true".equalsIgnoreCase(si.getValue());
    }

    // ── Setter used by NidanPaywallConfigRestController ───────────────────────

    /**
     * Persists the three boolean flags in a single logical operation. Each flag is
     * written only if the row already exists (Liquibase guarantees the rows are
     * present after first boot).
     *
     * @param allowOpd whether OPD orders bypass the paywall
     * @param allowIpd whether IPD orders bypass the paywall
     * @param allowEr  whether ER orders bypass the paywall
     */
    public void saveConfig(boolean allowOpd, boolean allowIpd, boolean allowEr) {
        persist(KEY_OPD, allowOpd);
        persist(KEY_IPD, allowIpd);
        persist(KEY_ER, allowEr);
    }

    private void persist(String key, boolean value) {
        SiteInformation si = siteInformationService.getSiteInformationByName(key);
        if (si == null) {
            // Row not yet seeded — silently skip; Liquibase will create it on
            // next boot and the default value is safe.
            return;
        }
        si.setValue(value ? "true" : "false");
        siteInformationService.update(si);
    }
}
