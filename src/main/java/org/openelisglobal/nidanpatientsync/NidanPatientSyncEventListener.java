package org.openelisglobal.nidanpatientsync;

import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link PatientSavedEvent} and forwards patient data to nidan-cis
 * asynchronously.
 *
 * <p>
 * Pattern mirrors {@code TestOrderEventListener} exactly:
 * {@code @Async @EventListener} on the event class — no extra service layer
 * needed.
 *
 * <p>
 * Fires on both patient create and update. Never sends subjectNumber.
 */
@Component
public class NidanPatientSyncEventListener {

    private static final Logger LOG = LogManager.getLogger(NidanPatientSyncEventListener.class);
    private static final String LOG_PREFIX = "[NIDAN-PATIENT-SYNC]";

    @Autowired
    private NidanPatientSyncClient patientSyncClient;

    @Async
    @EventListener
    public void onPatientSaved(PatientSavedEvent event) {
        try {
            PatientManagementInfo info = event.getPatientInfo();

            if (info == null) {
                LOG.warn("{} PatientSavedEvent had null patientInfo — skipping", LOG_PREFIX);
                return;
            }

            String guid = info.getGuid();
            if (GenericValidator.isBlankOrNull(guid)) {
                LOG.warn("{} Patient has no guid — skipping sync", LOG_PREFIX);
                return;
            }

            String changeType = event.isCreate() ? "created" : "updated";

            NidanPatientSyncNotification notification = new NidanPatientSyncNotification(guid,
                    nullSafe(info.getFirstName()), nullSafe(info.getLastName()), nullSafe(info.getGender()),
                    nullSafe(info.getBirthDateForDisplay()), nullSafe(info.getNationalId()), // null/blank is fine —
                                                                                             // middleware skips NID
                                                                                             // identifier if blank
                    changeType);

            patientSyncClient.send(notification);

        } catch (Exception e) {
            LOG.error("{} unexpected error in event listener: {}", LOG_PREFIX, e.getMessage(), e);
        }
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s.trim();
    }
}
