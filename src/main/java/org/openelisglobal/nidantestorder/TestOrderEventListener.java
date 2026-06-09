package org.openelisglobal.nidantestorder;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.common.services.SampleAddService;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.sample.action.util.SamplePatientUpdateData;
import org.openelisglobal.sample.event.SamplePatientUpdateDataCreatedEvent;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.openelisglobal.nidantestorder.TestOrderDiffService.DiffResult;

@Component
public class TestOrderEventListener {

    private static final Logger LOG = LogManager.getLogger(TestOrderEventListener.class);

    @Autowired
    private TestOrderClient testOrderClient;

    @Autowired
    private TestOrderDiffService testOrderDiffService;

    @Autowired
    private TestService testService;

    @Async
    @EventListener
    public void onSampleCollected(SamplePatientUpdateDataCreatedEvent event) {
        try {
            SamplePatientUpdateData updateData = event.getUpdateData();

            // patientGuid — null if not a NIDAN patient
            String patientGuid = null;
            if (event.getPatientInfo() != null
                    && !GenericValidator.isBlankOrNull(event.getPatientInfo().getGuid())) {
                patientGuid = event.getPatientInfo().getGuid();
            }

            // visitUuid — null if manual sample (not from external order)
            String visitUuid = null;
            if (!GenericValidator.isBlankOrNull(updateData.getReferringId())) {
                visitUuid = updateData.getReferringId();
            }

            String accessionNumber = updateData.getAccessionNumber();

            SampleAddService sampleAddService = updateData.getSampleAddService();

            List<TestOrderNotification.TestRef> testRefs = new ArrayList<>();
            if (updateData.getSampleItemsTests() != null) {
                for (SampleTestCollection stc : updateData.getSampleItemsTests()) {
                    if (stc.tests == null) {
                        continue;
                    }
                    for (Test stub : stc.tests) {
                        if (GenericValidator.isBlankOrNull(stub.getId())) {
                            continue;
                        }

                        // fetch full hydrated Test — stub only has id set
                        Test full = testService.get(stub.getId());
                        if (full == null) {
                            LOG.warn("[NIDAN-TESTORDER] testService.get({}) returned null — skipping", stub.getId());
                            continue;
                        }

                        String testGuid = GenericValidator.isBlankOrNull(full.getGuid()) ? null : full.getGuid();
                        String loinc = GenericValidator.isBlankOrNull(full.getLoinc()) ? null : full.getLoinc();

                        // resolve panel for this test — null if test not part of a panel
                        String panelGuid = null;
                        if (sampleAddService != null) {
                            try {
                                Panel panel = sampleAddService.getPanelForTest(full);
                                if (panel != null && !GenericValidator.isBlankOrNull(panel.getGuid())) {
                                    panelGuid = panel.getGuid();
                                }
                            } catch (Exception panelEx) {
                                // getPanelForTest throws if createSampleTestCollection not called first
                                // or if panel can't be resolved — safe to swallow
                                LOG.debug("[NIDAN-TESTORDER] panel resolution skipped for testId={}: {}",
                                        full.getId(), panelEx.getMessage());
                            }
                        }

                        LOG.info("[NIDAN-TESTORDER] resolved testId={} testGuid={} panelGuid={} loinc={}",
                                full.getId(),
                                testGuid != null ? testGuid : "<null>",
                                panelGuid != null ? panelGuid : "<null>",
                                loinc != null ? loinc : "<null>");

                        testRefs.add(new TestOrderNotification.TestRef(testGuid, panelGuid, loinc));
                    }
                }
            }

            // ── Diff filter ───────────────────────────────────────────────────────────────
            // If this sample came from an external order (visitUuid set), only forward
            // tests that were NOT already present in the original order — those were sent
            // to middleware/Odoo at order-receipt time and must not be duplicated.
            // Manual ELIS entries (visitUuid null) always pass through unchanged.
            DiffResult diff = testOrderDiffService.diff(visitUuid, testRefs);

            if (diff.isExternalOrder() && diff.netNewTests().isEmpty()) {
                LOG.info("[NIDAN-TESTORDER] all tests were in original order — suppressing notification for accession={}",
                        accessionNumber);
                return;
            }

            List<TestOrderNotification.TestRef> testsToSend = diff.isExternalOrder()
                    ? diff.netNewTests()
                    : testRefs;

            testOrderClient.sendTestOrder(new TestOrderNotification(
                    patientGuid, visitUuid, accessionNumber, testsToSend));

        } catch (Exception e) {
            LOG.error("[NIDAN-TESTORDER] failed for accession={}: {}",
                    event.getUpdateData() != null ? event.getUpdateData().getAccessionNumber() : "unknown",
                    e.getMessage(), e);
        }
    }
}
