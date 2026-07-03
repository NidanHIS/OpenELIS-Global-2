package org.openelisglobal.nidancisretry;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP client — GET dead letters from CIS, filter, POST retry. No threading
 * here. Called by {@link NidanCisRetryService} which owns @Async.
 *
 * <p>
 * Config:
 * <ul>
 * <li>{@code org.openelisglobal.nidan.cisretry.list.url} — full URL to GET dead
 * letters</li>
 * <li>{@code org.openelisglobal.nidan.cisretry.retry.url} — full URL to POST
 * retry</li>
 * <li>{@code org.openelisglobal.middleware.result.sync.secret} — shared webhook
 * secret (reused)</li>
 * </ul>
 */
@Component
public class NidanCisRetryClient {

    private static final Logger LOG = LogManager.getLogger(NidanCisRetryClient.class);

    private static final String SECRET_HEADER = "X-Nidan-Webhook-Secret";
    private static final int LIST_LIMIT = 300;
    private static final int RETRY_CAP = 150;

    @Value("${org.openelisglobal.nidan.cisretry.list.url:}")
    private String listUrl;

    @Value("${org.openelisglobal.nidan.cisretry.retry.url:}")
    private String retryUrl;

    @Value("${org.openelisglobal.middleware.result.sync.secret:change-me}")
    private String secret;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Executes the full retry sequence synchronously. Intended to be called from
     * {@link NidanCisRetryService#retryAsync()} which is @Async.
     */
    public void execute() {
        LOG.info("[NIDAN-CIS-RETRY] ══════════════════════════════════════════");

        if (GenericValidator.isBlankOrNull(listUrl)) {
            LOG.warn("[NIDAN-CIS-RETRY] org.openelisglobal.nidan.cisretry.list.url not set — aborting");
            LOG.info("[NIDAN-CIS-RETRY] ══════════════════════════════════════════");
            return;
        }

        if (GenericValidator.isBlankOrNull(retryUrl)) {
            LOG.warn("[NIDAN-CIS-RETRY] org.openelisglobal.nidan.cisretry.retry.url not set — aborting");
            LOG.info("[NIDAN-CIS-RETRY] ══════════════════════════════════════════");
            return;
        }

        try {
            // Step 1: GET dead letters
            String listUrlWithParams = listUrl + "?status=NON_SUCCESS&limit=" + LIST_LIMIT;
            LOG.info("[NIDAN-CIS-RETRY] GET {}", listUrlWithParams);

            List<Map<String, Object>> rows = cisGet(listUrlWithParams);
            LOG.info("[NIDAN-CIS-RETRY] Fetched {} dead letter rows", rows.size());

            // Step 2: filter CIS-PATIENTS-OPENELIS + CIS-ELIS-ORDERS, skip SUCCEEDED +
            // RETRY_REQUESTED
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (!isEligible(row)) {
                    continue;
                }
                Object idObj = row.get("id");
                if (idObj instanceof Number) {
                    ids.add(((Number) idObj).longValue());
                } else if (idObj != null) {
                    try {
                        ids.add(Long.parseLong(idObj.toString()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            LOG.info("[NIDAN-CIS-RETRY] Eligible CIS→ELIS rows: {}", ids.size());

            if (ids.isEmpty()) {
                LOG.info("[NIDAN-CIS-RETRY] Nothing to retry — done");
                LOG.info("[NIDAN-CIS-RETRY] ══════════════════════════════════════════");
                return;
            }

            // Step 3: cap at 150
            if (ids.size() > RETRY_CAP) {
                LOG.warn("[NIDAN-CIS-RETRY] Capping {} → {} (RETRY_CAP)", ids.size(), RETRY_CAP);
                ids = ids.subList(0, RETRY_CAP);
            }

            // Step 4: POST retry — full URL from property, used verbatim
            LOG.info("[NIDAN-CIS-RETRY] POST {} ids={}", retryUrl, ids.size());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ids", ids);

            Map<String, Object> result = cisPost(retryUrl, payload);
            LOG.info("[NIDAN-CIS-RETRY] Done: requested={} failed={}", result.get("requested"), result.get("failed"));

        } catch (Exception e) {
            LOG.error("[NIDAN-CIS-RETRY] Retry failed: {}", e.getMessage(), e);
        }

        LOG.info("[NIDAN-CIS-RETRY] ══════════════════════════════════════════");
    }

    private boolean isEligible(Map<String, Object> row) {
        Object sourceObj = row.get("source");
        if (sourceObj == null) {
            return false;
        }
        String source = sourceObj.toString().trim();
        if (!"CIS-PATIENTS-OPENELIS".equals(source) && !"CIS-ELIS-ORDERS".equals(source)) {
            return false;
        }
        Object statusObj = row.get("status");
        if (statusObj != null) {
            String status = statusObj.toString().trim().toUpperCase();
            if ("SUCCEEDED".equals(status) || "RETRY_REQUESTED".equals(status)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cisGet(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty(SECRET_HEADER, secret);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                throw new RuntimeException("CIS returned HTTP " + status + " with no body");
            }
            try {
                Object parsed = json.readValue(is, Object.class);
                if (parsed instanceof List) {
                    return (List<Map<String, Object>>) parsed;
                }
                throw new RuntimeException("Expected JSON array, got " + parsed.getClass().getSimpleName());
            } finally {
                is.close();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cisPost(String url, Map<String, Object> payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty(SECRET_HEADER, secret);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(60_000);
            conn.setDoOutput(true);

            byte[] body = json.writeValueAsBytes(payload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                throw new RuntimeException("CIS returned HTTP " + status + " with no body");
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
}
