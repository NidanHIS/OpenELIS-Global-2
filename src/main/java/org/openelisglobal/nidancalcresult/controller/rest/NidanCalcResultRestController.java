package org.openelisglobal.nidancalcresult.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.note.service.NoteService;
import org.openelisglobal.note.service.NoteServiceImpl.NoteType;
import org.openelisglobal.note.valueholder.Note;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/nidanCalcResult")
public class NidanCalcResultRestController extends BaseRestController {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private NoteService noteService;

    private static final String CALCULATION_SUBJECT = "Calculated Result Note";

    @PostMapping(value = "/accept", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> acceptCalculation(@RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String analysisId = (String) payload.get("analysisId");
            if (GenericValidator.isBlankOrNull(analysisId)) {
                response.put("success", false);
                response.put("message", "analysisId is required");
                return ResponseEntity.badRequest().body(response);
            }

            Analysis analysis = analysisService.get(analysisId);
            if (analysis == null) {
                response.put("success", false);
                response.put("message", "Analysis not found: " + analysisId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            String calcName = analysis.getPendingCalculationName();
            String calcValue = analysis.getPendingCalculatedValue();

            // Record audit note that result was accepted from calculation rule
            String noteText = String.format("[Result was Auto-Calculated from rule: %s with value: %s]",
                    calcName != null ? calcName : "unknown", calcValue != null ? calcValue : "");
            String sysUserId = getSysUserId(request);
            if (GenericValidator.isBlankOrNull(sysUserId)) {
                sysUserId = "1";
            }
            Note note = noteService.createSavableNote(analysis, NoteType.INTERNAL, noteText, CALCULATION_SUBJECT,
                    sysUserId);
            if (!noteService.duplicateNoteExists(note)) {
                noteService.save(note);
            }

            // Clear pending fields now that it's accepted
            analysis.setPendingCalculatedValue(null);
            analysis.setPendingCalculationName(null);
            analysis.setSysUserId(sysUserId);
            analysisService.update(analysis);

            response.put("success", true);
            response.put("action", "accepted");
            response.put("calculatedValue", calcValue);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogEvent.logError(e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping(value = "/dismiss", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> dismissCalculation(@RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String analysisId = (String) payload.get("analysisId");
            if (GenericValidator.isBlankOrNull(analysisId)) {
                response.put("success", false);
                response.put("message", "analysisId is required");
                return ResponseEntity.badRequest().body(response);
            }

            Analysis analysis = analysisService.get(analysisId);
            if (analysis == null) {
                response.put("success", false);
                response.put("message", "Analysis not found: " + analysisId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Clear pending fields so banner is dismissed
            String sysUserId = getSysUserId(request);
            if (GenericValidator.isBlankOrNull(sysUserId)) {
                sysUserId = "1";
            }
            analysis.setPendingCalculatedValue(null);
            analysis.setPendingCalculationName(null);
            analysis.setSysUserId(sysUserId);
            analysisService.update(analysis);

            response.put("success", true);
            response.put("action", "dismissed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogEvent.logError(e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
