package org.openelisglobal.nidanpaywall;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calls the Odoo payment-status API and returns a typed decision for a
 * patient/visit. Used to gate the "Collect Sample" action on incoming orders.
 *
 * <p>
 * Config mirrors the {@code org.openelisglobal.nidan.testorder.*} pattern:
 * properties in {@code common.properties}, driven by {@code -D} flags in
 * {@code CATALINA_OPTS} from {@code nidan-docker}.
 *
 * <p>
 * When {@code enabled=false} the check is skipped and always returns
 * {@link PaywallDecision#ALLOW} without contacting Odoo.
 */
@Component
public class NidanPaywallClient {

    private static final Logger LOG = LogManager.getLogger(NidanPaywallClient.class);
    private static final String SECRET_HEADER = "X-Nidan-Webhook-Secret";
    private static final String PAYMENT_STATUS_PATH = "/api/nidan/patient/payment-status";

    @Value("${org.openelisglobal.nidan.paywall.enabled:false}")
    private boolean enabled;

    @Value("${org.openelisglobal.nidan.paywall.odoo.url:}")
    private String odooUrl;

    @Value("${org.openelisglobal.nidan.paywall.odoo.secret:}")
    private String secret;

    @Value("${org.openelisglobal.nidan.paywall.insurance.bypass:false}")
    private boolean insuranceBypass;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Check whether a patient is cleared to have their sample collected.
     *
     * @param patientGuid         OpenMRS patient GUID (from
     *                            {@code IncomingOrder.patientGuid})
     * @param externalOrderNumber visit UUID sent by CIS (from
     *                            {@code IncomingOrder.externalOrderNumber})
     * @return result — never null; {@link PaywallResult#decision()} is the thing to
     *         act on
     */
    public PaywallResult check(String patientGuid, String externalOrderNumber) {
        LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
        LOG.info("[NIDAN-PAYWALL] patientGuid={}", blank(patientGuid));
        LOG.info("[NIDAN-PAYWALL] visitUuid={}", blank(externalOrderNumber));

        if (!enabled) {
            LOG.info("[NIDAN-PAYWALL] disabled — allow");
            LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
            return PaywallResult.allow();
        }

        if (GenericValidator.isBlankOrNull(odooUrl)) {
            LOG.warn(
                    "[NIDAN-PAYWALL] enabled=true but org.openelisglobal.nidan.paywall.odoo.url not set — allowing (outage)");
            LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
            return PaywallResult.outage();
        }

        if (GenericValidator.isBlankOrNull(secret)) {
            LOG.warn(
                    "[NIDAN-PAYWALL] enabled=true but org.openelisglobal.nidan.paywall.odoo.secret not set — allowing (outage)");
            LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
            return PaywallResult.outage();
        }

        if (GenericValidator.isBlankOrNull(patientGuid)) {
            LOG.warn("[NIDAN-PAYWALL] patientGuid is blank — allowing");
            LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
            return PaywallResult.allow();
        }

        try {
            String url = buildUrl(patientGuid, externalOrderNumber);
            LOG.info("[NIDAN-PAYWALL] GET {}", url);

            Map<String, Object> body = odooGet(url);
            PaywallResult result = toResult(body, insuranceBypass);

            LOG.info("[NIDAN-PAYWALL] decision={} outstanding={} status={}", result.decision(),
                    result.outstandingAmount(), result.paymentStatus());
            LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
            return result;

        } catch (Exception e) {
            LOG.warn("[NIDAN-PAYWALL] Odoo call failed (outage) — allowing: {}", e.getMessage());
            LOG.info("[NIDAN-PAYWALL] ══════════════════════════════════════════");
            return PaywallResult.outage();
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private String buildUrl(String patientGuid, String visitUuid) throws java.io.UnsupportedEncodingException {
        String base = odooUrl.endsWith("/") ? odooUrl.substring(0, odooUrl.length() - 1) : odooUrl;
        StringBuilder q = new StringBuilder();
        q.append("patient_uuid=").append(java.net.URLEncoder.encode(patientGuid.trim(), "UTF-8"));
        if (!GenericValidator.isBlankOrNull(visitUuid)) {
            q.append("&visit_uuid=").append(java.net.URLEncoder.encode(visitUuid.trim(), "UTF-8"));
        }
        return base + PAYMENT_STATUS_PATH + "?" + q;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> odooGet(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty(SECRET_HEADER, secret);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(10_000);

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                throw new RuntimeException("Odoo returned HTTP " + status + " with no body");
            }
            try {
                return json.readValue(is, Map.class);
            } finally {
                is.close();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static PaywallResult toResult(Map<String, Object> r, boolean insuranceBypass) {
        if (r == null) {
            return PaywallResult.allow(); // null body = no billing = allow
        }

        // Odoo structured error: PATIENT_NOT_FOUND / VISIT_NOT_FOUND = no billing =
        // allow
        if (r.containsKey("error")) {
            String err = String.valueOf(r.get("error"));
            if ("PATIENT_NOT_FOUND".equals(err) || "VISIT_NOT_FOUND".equals(err)) {
                LOG.info("[NIDAN-PAYWALL] odoo={}  → allow (no billing record)", err);
                return PaywallResult.allow();
            }
            throw new RuntimeException("Odoo error: " + err);
        }

        // ── 1. RAW ODOO FIELDS ───────────────────────────────────────────────
        LOG.info("[NIDAN-PAYWALL] odoo → is_insured={} is_settled={} due={} cart={} status={}", r.get("is_insured"),
                r.get("is_settled"), r.get("due_amount"), r.get("cart_amount"), r.get("payment_status"));

        // ── 2. INSURANCE BYPASS GATE ─────────────────────────────────────────
        boolean insured = r.containsKey("is_insured") ? asBool(r.get("is_insured")) : false;
        LOG.info("[NIDAN-PAYWALL] insurance-bypass cfg={} patient-insured={} → bypass={}", insuranceBypass, insured,
                (insuranceBypass && insured));

        if (insuranceBypass && insured) {
            LOG.info("[NIDAN-PAYWALL] verdict=allow  reason=insurance-bypass → forwarding to frontend");
            return new PaywallResult("allow", 0.0, asString(r.get("currency")), asString(r.get("payment_status")));
        }

        // ── 3. STANDARD SETTLEMENT CHECK ────────────────────────────────────
        double due = asDouble(r.get("due_amount"));
        double cart = asDouble(r.get("cart_amount"));
        String currency = asString(r.get("currency"));
        String paymentStatus = asString(r.get("payment_status"));
        double outstanding = due + cart;

        boolean settled = r.containsKey("is_settled") ? asBool(r.get("is_settled")) : (due <= 0.0 && cart <= 0.0);

        if (settled) {
            LOG.info("[NIDAN-PAYWALL] verdict=allow  reason=settled outstanding={} → forwarding to frontend",
                    outstanding);
            return new PaywallResult("allow", outstanding, currency, paymentStatus);
        }
        LOG.info("[NIDAN-PAYWALL] verdict=block  reason=outstanding={} settled={} → forwarding to frontend",
                outstanding, settled);
        return new PaywallResult("block", outstanding, currency, paymentStatus);
    }

    // --- tiny type helpers ---

    private static boolean asBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o != null && "true".equalsIgnoreCase(o.toString());
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? "<null>" : s;
    }
}
