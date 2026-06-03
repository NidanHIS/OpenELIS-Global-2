package org.openelisglobal.nidantestorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.dataexchange.common.ReportTransmission;
import org.openelisglobal.dataexchange.common.ReportTransmission.HTTP_TYPE;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TestOrderClient {

    private static final Logger LOG = LogManager.getLogger(TestOrderClient.class);
    private static final String SECRET_HEADER = "X-Nidan-Webhook-Secret";

    @Value("${org.openelisglobal.nidan.testorder.enabled:false}")
    private boolean enabled;

    @Value("${org.openelisglobal.nidan.testorder.url:}")
    private String endpointUrl;

    @Value("${org.openelisglobal.middleware.result.sync.secret:change-me}")
    private String secret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendTestOrder(TestOrderNotification notification) {
        if (notification == null) {
            LOG.warn("[NIDAN-TESTORDER] sendTestOrder called with null — skipping");
            return;
        }

        LOG.info("[NIDAN-TESTORDER] ══════════════════════════════════════════");
        LOG.info("[NIDAN-TESTORDER] patientGuid={}", blank(notification.patientGuid()));
        LOG.info("[NIDAN-TESTORDER] visitUuid={}", blank(notification.visitUuid()));
        LOG.info("[NIDAN-TESTORDER] accessionNumber={}", notification.accessionNumber());
        LOG.info("[NIDAN-TESTORDER] testCount={}", notification.tests() != null ? notification.tests().size() : 0);
        if (notification.tests() != null) {
            for (int i = 0; i < notification.tests().size(); i++) {
                TestOrderNotification.TestRef t = notification.tests().get(i);
                LOG.info("[NIDAN-TESTORDER]   test[{}] testGuid={} panelGuid={} loinc={}",
                        i, blank(t.testGuid()), blank(t.panelGuid()), blank(t.loincCode()));
            }
        }

        if (!enabled) {
            LOG.info("[NIDAN-TESTORDER] disabled — logged only");
            LOG.info("[NIDAN-TESTORDER] ══════════════════════════════════════════");
            return;
        }

        if (GenericValidator.isBlankOrNull(endpointUrl)) {
            LOG.warn("[NIDAN-TESTORDER] enabled=true but org.openelisglobal.nidan.testorder.url not set — skipping HTTP");
            LOG.info("[NIDAN-TESTORDER] ══════════════════════════════════════════");
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(notification);
            LOG.info("[NIDAN-TESTORDER] POST {} payload={}", endpointUrl, json);

            ReportTransmission transmission = new ReportTransmission();
            transmission.sendRawReport(json, endpointUrl, true, null, HTTP_TYPE.POST, SECRET_HEADER, secret);

            LOG.info("[NIDAN-TESTORDER] POST sent successfully");
        } catch (Exception e) {
            LOG.error("[NIDAN-TESTORDER] POST failed: {}", e.getMessage(), e);
        }

        LOG.info("[NIDAN-TESTORDER] ══════════════════════════════════════════");
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? "<null>" : s;
    }
}
