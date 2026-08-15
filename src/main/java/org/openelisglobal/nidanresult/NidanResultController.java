package org.openelisglobal.nidanresult;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.common.util.ControllerUtills;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the NIDAN result bridge API.
 *
 * <p>
 * Provides lightweight endpoints for external systems to post lab results
 * programmatically without the full OpenELIS-Global-2 UI form workflow. All
 * endpoints use basic auth for authentication.
 *
 * <h2>Endpoints</h2>
 * <ul>
 * <li>{@code GET /rest/nidan/result/health} — health check</li>
 * <li>{@code GET /rest/nidan/result/tests} — list all available tests</li>
 * <li>{@code GET /rest/nidan/result/pending} — list pending analyses for a
 * sample</li>
 * <li>{@code POST /rest/nidan/result/entry} — submit a single result</li>
 * </ul>
 *
 * <h2>Authentication</h2> Include an
 * {@code Authorization: Basic base64(username:password)} header with valid
 * OpenELIS user credentials. The same users that can log into the web UI can
 * use this API.
 *
 * <h2>Error handling</h2> All errors return structured JSON with
 * {@code status: "error"} and a descriptive {@code message} field. HTTP status
 * codes follow REST conventions: 400 for bad input, 404 for not found, 500 for
 * server errors.
 */
@RestController
@RequestMapping("/rest/nidan/result")
public class NidanResultController {

    private static final Logger LOG = LogManager.getLogger(NidanResultController.class);
    private static final String LOG_PREFIX = "[NIDAN-RESULT-API]";

    @Autowired
    private NidanResultService nidanResultService;

    /**
     * Health check endpoint.
     *
     * @return {@code 200} with {@code {status: "success", message: "NIDAN Result
     *         API is operational"}}
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "NIDAN Result API is operational");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all available tests in the system.
     *
     * <p>
     * Returns active tests that can be referenced when interpreting results or
     * understanding test configurations.
     *
     * @param orderableOnly optional query parameter — if {@code true} (default),
     *                      returns only tests marked as orderable; if
     *                      {@code false}, returns all active tests
     * @return {@code 200} with an array of {@link NidanTestItem} objects
     * @throws 500 for system errors
     */
    @GetMapping(value = "/tests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getAllTests(@RequestParam(defaultValue = "true") boolean orderableOnly,
            HttpServletRequest request) {

        try {
            String sysUserId = ControllerUtills.getSysUserId(request);
            if (GenericValidator.isBlankOrNull(sysUserId)) {
                LOG.warn("{} unauthenticated request for tests list", LOG_PREFIX);
                return errorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }

            List<NidanTestItem> items = nidanResultService.getAllTests(orderableOnly);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("tests", items);
            response.put("count", items.size());
            response.put("orderableOnly", orderableOnly);

            LOG.info("{} returned {} tests (orderableOnly={}) for user={}", LOG_PREFIX, items.size(), orderableOnly,
                    sysUserId);

            return ResponseEntity.ok(response);

        } catch (NidanResultException e) {
            return handleNidanException(e);
        } catch (Exception e) {
            LOG.error("{} unexpected error in getAllTests: {}", LOG_PREFIX, e.getMessage(), e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected system error: " + e.getMessage());
        }
    }

    /**
     * Lists all pending analyses for a given sample number.
     *
     * <p>
     * Pending analyses are those in {@code NotStarted} or
     * {@code TechnicalAcceptance} status — tests that can accept new results.
     *
     * @param sampleNumber required query parameter — the NIDAN sample number (e.g.
     *                     {@code 1508-0001})
     * @return {@code 200} with an array of {@link NidanPendingResultItem} objects
     * @throws 400 if {@code sampleNumber} is missing or blank
     * @throws 404 if the sample does not exist
     * @throws 500 for system errors
     */
    @GetMapping(value = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getPendingResults(@RequestParam String sampleNumber,
            HttpServletRequest request) {

        try {
            // input validation
            if (GenericValidator.isBlankOrNull(sampleNumber)) {
                return errorResponse(HttpStatus.BAD_REQUEST, "sampleNumber query parameter is required");
            }

            String sysUserId = ControllerUtills.getSysUserId(request);
            if (GenericValidator.isBlankOrNull(sysUserId)) {
                LOG.warn("{} unauthenticated request for sampleNumber={}", LOG_PREFIX, sampleNumber);
                return errorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }

            List<NidanPendingResultItem> items = nidanResultService.getPendingResults(sampleNumber.trim());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("sampleNumber", sampleNumber.trim());
            response.put("pendingResults", items);
            response.put("count", items.size());

            LOG.info("{} returned {} pending results for sampleNumber={} (user={})", LOG_PREFIX, items.size(),
                    sampleNumber.trim(), sysUserId);

            return ResponseEntity.ok(response);

        } catch (NidanResultException e) {
            return handleNidanException(e);
        } catch (Exception e) {
            LOG.error("{} unexpected error in getPendingResults: {}", LOG_PREFIX, e.getMessage(), e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected system error: " + e.getMessage());
        }
    }

    /**
     * Submits a single result for an analysis.
     *
     * <p>
     * The request body must contain:
     * <ul>
     * <li>{@code sampleNumber} — the NIDAN sample number</li>
     * <li>{@code analysisId} — the OpenELIS analysis ID (from
     * {@code /pending})</li>
     * <li>{@code resultValue} — the result value to persist</li>
     * <li>{@code resultType} — optional, defaults to {@code "N"} (numeric)</li>
     * <li>{@code testDate} — optional, defaults to today</li>
     * <li>{@code note} — optional internal note</li>
     * </ul>
     *
     * @param request the validated request payload
     * @return {@code 200} with a {@link NidanResultResponse} on success
     * @throws 400 for validation errors or business rule violations
     * @throws 404 if the sample or analysis does not exist
     * @throws 500 for system errors
     */
    @PostMapping(value = "/entry", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NidanResultResponse> submitResult(@RequestBody NidanResultRequest request,
            HttpServletRequest httpRequest) {

        try {
            String sysUserId = ControllerUtills.getSysUserId(httpRequest);
            if (GenericValidator.isBlankOrNull(sysUserId)) {
                LOG.warn("{} unauthenticated result submission attempt for sampleNumber={}", LOG_PREFIX,
                        request != null ? request.getSampleNumber() : "null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(NidanResultResponse.error("Authentication required"));
            }

            NidanResultResponse response = nidanResultService.submitResult(request, sysUserId, httpRequest);

            LOG.info("{} result submitted: sampleNumber={}, analysisId={}, status={} (user={})", LOG_PREFIX,
                    request.getSampleNumber(), request.getAnalysisId(), response.getResultStatus(), sysUserId);

            return ResponseEntity.ok(response);

        } catch (NidanResultException e) {
            LOG.warn("{} result submission failed: {}", LOG_PREFIX, e.getMessage());
            return ResponseEntity.status(mapExceptionToHttpStatus(e)).body(NidanResultResponse.error(e.getMessage()));
        } catch (Exception e) {
            LOG.error("{} unexpected error in submitResult: {}", LOG_PREFIX, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(NidanResultResponse.error("Unexpected system error: " + e.getMessage()));
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Maps a {@link NidanResultException} to the appropriate HTTP status code.
     */
    private HttpStatus mapExceptionToHttpStatus(NidanResultException e) {
        switch (e.getKind()) {
        case BAD_REQUEST:
            return HttpStatus.BAD_REQUEST;
        case NOT_FOUND:
            return HttpStatus.NOT_FOUND;
        case INTERNAL_ERROR:
        default:
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Handles a {@link NidanResultException} and returns an appropriate response
     * for GET endpoints that return {@code Map<String, Object>}.
     */
    private ResponseEntity<Map<String, Object>> handleNidanException(NidanResultException e) {
        HttpStatus status = mapExceptionToHttpStatus(e);
        LOG.warn("{} handled exception ({}): {}", LOG_PREFIX, e.getKind(), e.getMessage());
        return errorResponse(status, e.getMessage());
    }

    /**
     * Creates a structured error response for GET endpoints.
     */
    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(status).body(response);
    }
}