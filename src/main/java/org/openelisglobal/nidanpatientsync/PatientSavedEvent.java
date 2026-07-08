package org.openelisglobal.nidanpatientsync;

import org.openelisglobal.patient.action.bean.PatientManagementInfo;

/**
 * Spring application event published after a patient is successfully persisted
 * via {@code POST /rest/PatientManagement} or
 * {@code POST /rest/CredentialPatientManagement}.
 *
 * <p>
 * Consumed asynchronously by {@link NidanPatientSyncEventListener}.
 */
public class PatientSavedEvent {

    private final PatientManagementInfo patientInfo;
    private final boolean isCreate;

    public PatientSavedEvent(PatientManagementInfo patientInfo, boolean isCreate) {
        this.patientInfo = patientInfo;
        this.isCreate = isCreate;
    }

    public PatientManagementInfo getPatientInfo() {
        return patientInfo;
    }

    public boolean isCreate() {
        return isCreate;
    }
}
