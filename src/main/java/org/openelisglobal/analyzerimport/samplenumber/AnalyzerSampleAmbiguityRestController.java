package org.openelisglobal.analyzerimport.samplenumber;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.log.LogEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the analyzer sample ambiguity governing queue.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /rest/nidan/analyzer-ambiguity/pending} — list all PENDING cards</li>
 *   <li>{@code GET  /rest/nidan/analyzer-ambiguity/pending/count} — count of PENDING rows</li>
 *   <li>{@code POST /rest/nidan/analyzer-ambiguity/resolve} — user chooses a candidate sample</li>
 *   <li>{@code POST /rest/nidan/analyzer-ambiguity/discard} — user discards the result</li>
 * </ul>
 *
 * <p>All endpoints are fail-safe: exceptions return structured error responses; no 500s propagate.
 */
@RestController
@RequestMapping("/rest/nidan/analyzer-ambiguity")
public class AnalyzerSampleAmbiguityRestController extends BaseController {

    @Autowired
    private AnalyzerSampleAmbiguityService ambiguityService;

    // ── GET pending list ─────────────────────────────────────────────────────

    @GetMapping(value = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listPending() {
        try {
            List<AnalyzerSampleAmbiguityCard> cards = ambiguityService.listPending();
            return ResponseEntity.ok(cards != null ? cards : Collections.emptyList());
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "listPending", e.toString());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping(value = "/pending/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> countPending() {
        try {
            return ResponseEntity.ok(ambiguityService.countPending());
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "countPending", e.toString());
            return ResponseEntity.ok(0L);
        }
    }

    // ── POST resolve ─────────────────────────────────────────────────────────

    /**
     * User has chosen a candidate sample for a pending ambiguity row.
     * Body: {@code { "pendingId": 42, "sampleId": "123" }}
     * On success: result moves to analyzer_results holding, row marked RESOLVED.
     */
    @PostMapping(
            value = "/resolve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resolve(
            @RequestBody ResolveRequest body, HttpServletRequest request) {
        try {
            if (body == null || body.getPendingId() == null || body.getSampleId() == null
                    || body.getSampleId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("pendingId and sampleId are required"));
            }
            String sysUserId = getSysUserId(request);
            boolean ok = ambiguityService.promoteToHolding(
                    body.getPendingId(), body.getSampleId().trim(), sysUserId);
            if (ok) {
                return ResponseEntity.ok(new SuccessResponse("Resolved — result moved to holding"));
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new ErrorResponse(
                                "Could not resolve: check pendingId, sampleId, and row status"));
            }
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "resolve", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal error — result not moved"));
        }
    }

    // ── POST discard ─────────────────────────────────────────────────────────

    /**
     * User discards a pending ambiguity row. Result is dropped, no holding row written.
     * Body: {@code { "pendingId": 42 }}
     */
    @PostMapping(
            value = "/discard",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> discard(
            @RequestBody DiscardRequest body, HttpServletRequest request) {
        try {
            if (body == null || body.getPendingId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse("pendingId is required"));
            }
            String sysUserId = getSysUserId(request);
            boolean ok = ambiguityService.discard(body.getPendingId(), sysUserId);
            if (ok) {
                return ResponseEntity.ok(new SuccessResponse("Discarded"));
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new ErrorResponse(
                                "Could not discard: check pendingId and row status"));
            }
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "discard", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Internal error"));
        }
    }

    @Override
    protected String findLocalForward(String forward) {
        return null; // REST-only controller — no view forwarding
    }

    @Override
    protected String getPageTitleKey() {
        return ""; // REST-only controller — no page title
    }

    @Override
    protected String getPageSubtitleKey() {
        return ""; // REST-only controller — no page subtitle
    }

    // ── Request / response DTOs ───────────────────────────────────────────────

    public static class ResolveRequest {
        private Integer pendingId;
        private String sampleId;

        public Integer getPendingId() { return pendingId; }
        public void setPendingId(Integer pendingId) { this.pendingId = pendingId; }
        public String getSampleId() { return sampleId; }
        public void setSampleId(String sampleId) { this.sampleId = sampleId; }
    }

    public static class DiscardRequest {
        private Integer pendingId;

        public Integer getPendingId() { return pendingId; }
        public void setPendingId(Integer pendingId) { this.pendingId = pendingId; }
    }

    public static class SuccessResponse {
        private final String message;
        public SuccessResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }

    public static class ErrorResponse {
        private final String error;
        public ErrorResponse(String error) { this.error = error; }
        public String getError() { return error; }
    }
}
