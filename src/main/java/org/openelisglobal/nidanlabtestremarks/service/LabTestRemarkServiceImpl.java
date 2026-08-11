package org.openelisglobal.nidanlabtestremarks.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.nidanlabtestremarks.controller.rest.LabTestRemarkRestController.LabTestRemarkDTO;
import org.openelisglobal.nidanlabtestremarks.controller.rest.LabTestRemarkRestController.SaveLabTestRemarksPayload;
import org.openelisglobal.nidanlabtestremarks.controller.rest.LabTestRemarkRestController.TestPanelOptionDTO;
import org.openelisglobal.nidanlabtestremarks.dao.LabTestRemarkDAO;
import org.openelisglobal.nidanlabtestremarks.valueholder.LabTestRemark;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabTestRemarkServiceImpl implements LabTestRemarkService {

    private static final String TYPE_TEST = "TEST";
    private static final String TYPE_PANEL = "PANEL";

    @Autowired
    private LabTestRemarkDAO dao;

    @Autowired
    private TestService testService;

    @Autowired
    private PanelService panelService;

    @Override
    @Transactional(readOnly = true)
    public List<LabTestRemarkDTO> getAll() {
        try {
            return dao.getAll().stream()
                    .map(r -> {
                        String entityName = resolveEntityName(r.getEntityType(), r.getEntityId());
                        return new LabTestRemarkDTO(r.getId(), r.getEntityType(), r.getEntityId(), entityName, r.getRemarks());
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkService getAll()", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestPanelOptionDTO> getOptions() {
        try {
            List<TestPanelOptionDTO> options = new ArrayList<>();

            List<Test> activeTests = testService.getAllActiveTests(false);
            if (activeTests != null) {
                for (Test test : activeTests) {
                    String displayName = "[Test] " + getTestDisplayName(test);
                    options.add(new TestPanelOptionDTO(TYPE_TEST, Long.valueOf(test.getId()), displayName));
                }
            }

            List<Panel> activePanels = panelService.getAllActivePanels();
            if (activePanels != null) {
                for (Panel panel : activePanels) {
                    String displayName = "[Panel] " + getPanelDisplayName(panel);
                    options.add(new TestPanelOptionDTO(TYPE_PANEL, Long.valueOf(panel.getId()), displayName));
                }
            }

            options.sort(Comparator.comparing(TestPanelOptionDTO::displayName));
            return options;
        } catch (Exception e) {
            LogEvent.logError(e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public void save(SaveLabTestRemarksPayload payload, String sysUserId) {
        if (payload == null) {
            return;
        }

        try {
            // Step 1: Execute explicit deletions
            if (payload.deletedIds() != null) {
                for (Long deleteId : payload.deletedIds()) {
                    if (deleteId != null) {
                        dao.deleteById(deleteId);
                    }
                }
            }

            // Step 2: Upsert items to save
            if (payload.itemsToSave() != null) {
                for (LabTestRemarkDTO dto : payload.itemsToSave()) {
                    if (dto.entityType() == null || dto.entityId() == null) {
                        continue;
                    }
                    LabTestRemark remark = null;
                    if (dto.id() != null) {
                        remark = dao.getById(dto.id());
                    }
                    if (remark == null) {
                        remark = dao.getByEntityTypeAndId(dto.entityType(), dto.entityId());
                    }
                    if (remark == null) {
                        remark = new LabTestRemark();
                    }
                    remark.setEntityType(dto.entityType());
                    remark.setEntityId(dto.entityId());
                    remark.setRemarks(dto.remarks());
                    remark.setSysUserId(sysUserId);
                    dao.saveOrUpdate(remark);
                }
            }
        } catch (Exception e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkService save()", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getRemarkForEntity(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return "";
        }
        try {
            LabTestRemark remark = dao.getByEntityTypeAndId(entityType, entityId);
            if (remark != null && remark.getRemarks() != null) {
                return remark.getRemarks();
            }
        } catch (Exception e) {
            LogEvent.logError(e);
        }
        return "";
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private String resolveEntityName(String entityType, Long entityId) {
        try {
            if (TYPE_TEST.equals(entityType)) {
                Test test = testService.getTestById(String.valueOf(entityId));
                if (test != null) {
                    return getTestDisplayName(test);
                }
            } else if (TYPE_PANEL.equals(entityType)) {
                Panel panel = panelService.getPanelById(String.valueOf(entityId));
                if (panel != null) {
                    return getPanelDisplayName(panel);
                }
            }
        } catch (Exception ignored) {
        }
        return "[Unknown " + entityType + " (ID: " + entityId + ")]";
    }

    private String getTestDisplayName(Test test) {
        String name = test.getName();
        if (name == null || name.isBlank()) {
            name = test.getDescription();
        }
        return name != null ? name : String.valueOf(test.getId());
    }

    private String getPanelDisplayName(Panel panel) {
        String name = panel.getLocalizedName();
        if (name == null || name.isBlank()) {
            name = panel.getName();
        }
        return name != null ? name : String.valueOf(panel.getId());
    }
}
