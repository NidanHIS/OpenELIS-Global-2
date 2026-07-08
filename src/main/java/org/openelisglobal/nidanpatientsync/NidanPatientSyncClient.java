package org.openelisglobal.nidanpatientsync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.dataexchange.common.ReportTransmission;
import org.openelisglobal.dataexchange.common.ReportTransmission.HTTP_TYPE;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP client — POSTs a {@link NidanPatientSyncNotification} to nidan-cis
 * {@code /openelis/patient} on patient create or update.
 *
 * <p>
 * Config (set via CATALINA_OPTS -D flags from nidan-docker):
 * <ul>
 * <li>{@code org.openelisglobal.nidan.patientsync.enabled} — default
 * {@code false}</li>
 * <li>{@code org.openelisglobal.nidan.patientsync.url} — e.g.
 * {@code http://nidan-cis:8081/openelis/patient}</li>
 * <li>{@code org.openelisglobal.middleware.result.sync.secret} — shared webhook
 * secret (reused across all nidan clients)</li>
 * </ul>
 *
 * <p>
 * Pattern mirrors {@code TestOrderClient} exactly.
 */
@Component
public class NidanPatientSyncClient {

    private static final Logger LOG = LogManager.getLogger(NidanPatientSyncClient.class);
    private static final String LOG_PREFIX = "[NIDAN-PATIENT-SYNC]";
    private static final String SECRET_HEADER = "X-Nidan-Webhook-Secret";

    @Value("${org.openelisglobal.nidan.patientsync.enabled:false}")
    private boolean enabled;

    @Value("${org.openelisglobal.nidan.patientsync.url:}")
    private String endpointUrl;

    @Value("${org.openelisglobal.middleware.result.sync.secret:}")
    private String secret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sends the patient notification to middleware.
     *
     * @param notification never null
     */
    public void send(NidanPatientSyncNotification notification) {
        if (notification == null) {
            LOG.warn("{} send called with null notification — skipping", LOG_PREFIX);
            return;
        }

        LOG.info("{} ══════════════════════════════════════════", LOG_PREFIX);
        LOG.info("{} guid={}", LOG_PREFIX, blank(notification.guid()));
        LOG.info("{} changeType={}", LOG_PREFIX, notification.changeType());
        LOG.info("{} nationalId={}", LOG_PREFIX, blank(notification.nationalId()));

        if (!enabled) {
            LOG.info("{} disabled — logged only", LOG_PREFIX);
            LOG.info("{} ══════════════════════════════════════════", LOG_PREFIX);
            return;
        }

        if (GenericValidator.isBlankOrNull(endpointUrl)) {
            LOG.warn("{} enabled=true but org.openelisglobal.nidan.patientsync.url not set — skipping HTTP",
                    LOG_PREFIX);
            LOG.info("{} ══════════════════════════════════════════", LOG_PREFIX);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(notification);
            LOG.info("{} POST {} payload={}", LOG_PREFIX, endpointUrl, json);

            ReportTransmission transmission = new ReportTransmission();
            transmission.sendRawReport(json, endpointUrl, true, null, HTTP_TYPE.POST, SECRET_HEADER, secret);

            LOG.info("{} POST sent successfully", LOG_PREFIX);
        } catch (Exception e) {
            LOG.error("{} POST failed: {}", LOG_PREFIX, e.getMessage(), e);
        }

        LOG.info("{} ══════════════════════════════════════════", LOG_PREFIX);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? "<null>" : s;
    }
}
