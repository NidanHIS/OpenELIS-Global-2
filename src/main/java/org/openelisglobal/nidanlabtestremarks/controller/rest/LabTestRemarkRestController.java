package org.openelisglobal.nidanlabtestremarks.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.nidanlabtestremarks.service.LabTestRemarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/nidanLabTestRemarks")
public class LabTestRemarkRestController extends BaseRestController {

    @Autowired
    private LabTestRemarkService service;

    /**
     * Returns all saved remarks with dynamic entity display names resolved from TestService/PanelService.
     * GET /rest/nidanLabTestRemarks
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<LabTestRemarkDTO>> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());
        } catch (Exception e) {
            LogEvent.logError(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }

    /**
     * Returns combined list of active tests + panels for the searchable dropdown.
     * GET /rest/nidanLabTestRemarks/options
     */
    @GetMapping(value = "/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TestPanelOptionDTO>> getOptions() {
        try {
            return ResponseEntity.ok(service.getOptions());
        } catch (Exception e) {
            LogEvent.logError(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.emptyList());
        }
    }

    /**
     * Granular save: deletes explicitly requested IDs, then upserts itemsToSave.
     * POST /rest/nidanLabTestRemarks/save
     */
    @PostMapping(value = "/save",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> save(@RequestBody SaveLabTestRemarksPayload payload,
            HttpServletRequest request) {
        try {
            service.save(payload, getSysUserId(request));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LogEvent.logError(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record LabTestRemarkDTO(
            Long id,           // null for unsaved rows
            String entityType, // "TEST" or "PANEL"
            Long entityId,
            String entityName, // resolved dynamically from TestService/PanelService
            String remarks     // max 2000 chars
    ) {}

    public record TestPanelOptionDTO(
            String entityType,  // "TEST" or "PANEL"
            Long entityId,
            String displayName  // "[Test] Hemoglobin" or "[Panel] Hematology"
    ) {}

    public record SaveLabTestRemarksPayload(
            List<Long> deletedIds,
            List<LabTestRemarkDTO> itemsToSave
    ) {}
}
