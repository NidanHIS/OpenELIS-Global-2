package org.openelisglobal.nidanlabtestremarks.service;

import java.util.List;
import org.openelisglobal.nidanlabtestremarks.controller.rest.LabTestRemarkRestController.LabTestRemarkDTO;
import org.openelisglobal.nidanlabtestremarks.controller.rest.LabTestRemarkRestController.SaveLabTestRemarksPayload;
import org.openelisglobal.nidanlabtestremarks.controller.rest.LabTestRemarkRestController.TestPanelOptionDTO;

public interface LabTestRemarkService {

    /**
     * Returns all saved remarks with dynamic entity display names resolved from TestService/PanelService.
     */
    List<LabTestRemarkDTO> getAll();

    /**
     * Returns combined list of all active tests and panels for the searchable dropdown.
     */
    List<TestPanelOptionDTO> getOptions();

    /**
     * Granular save: deletes explicitly requested IDs, then upserts itemsToSave.
     */
    void save(SaveLabTestRemarksPayload payload, String sysUserId);
}
