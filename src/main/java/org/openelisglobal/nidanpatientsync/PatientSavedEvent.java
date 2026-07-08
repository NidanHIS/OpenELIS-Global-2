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
    private final boolean isRestCall;

    public PatientSavedEvent(PatientManagementInfo patientInfo, boolean isCreate) {
        this(patientInfo, isCreate, false);
    }

    public PatientSavedEvent(PatientManagementInfo patientInfo, boolean isCreate, boolean isRestCall) {
        this.patientInfo = patientInfo;
        this.isCreate = isCreate;
        this.isRestCall = isRestCall;
    }

    public PatientManagementInfo getPatientInfo() {
        return patientInfo;
    }

    public boolean isCreate() {
        return isCreate;
    }

    public boolean isRestCall() {
        return isRestCall;
    }
}
