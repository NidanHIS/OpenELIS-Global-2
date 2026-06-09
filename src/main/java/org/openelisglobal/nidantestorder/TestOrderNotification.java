package org.openelisglobal.nidantestorder;

import java.util.List;

public record TestOrderNotification(String patientGuid, // patient UUID — null if not a NIDAN patient
        String visitUuid, // externalOrderNumber (visit UUID) — null if manual sample
        String accessionNumber, // OpenELIS lab number — always present
        List<TestRef> tests) {

    public record TestRef(String testGuid, // Test.getGuid() — null if test has no GUID
            String panelGuid, // Panel.getGuid() — null if test not part of a panel
            String loincCode) { // Test.getLoinc() — null if no LOINC mapped
    }
}
