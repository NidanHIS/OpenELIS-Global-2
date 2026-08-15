package org.openelisglobal.nidanresult;

import java.util.List;

/**
 * Outbound payload from POST /rest/nidan/result/entry.
 *
 * <p>
 * {@code status} is always {@code "success"} on HTTP 200. On error the
 * controller returns HTTP 4xx/5xx with a body where {@code status} is
 * {@code "error"} and {@code message} carries the reason.
 *
 * <p>
 * {@code resultStatus} reflects what OpenELIS set on the analysis after persist
 * — typically {@code "TechnicalAcceptance"} or {@code "Finalized"} depending on
 * result limits and the {@code ALWAYS_VALIDATE_RESULTS} config.
 *
 * <p>
 * {@code reflexTriggered} and {@code calculatedTriggered} list the accession
 * numbers of any reflex / calculated analyses that were automatically created
 * as a side-effect of this result entry — mirrors what
 * {@code LogbookResultsRestController} returns in its
 * {@code reflex}/{@code calculated} maps.
 */
public class NidanResultResponse {

    private String status;
    private String message;
    private String sampleNumber;
    private String analysisId;
    private String accessionNumber;
    private String testName;
    private String resultValue;
    private String resultType;
    private String resultStatus;
    private List<String> reflexTriggered;
    private List<String> calculatedTriggered;

    // ── factory helpers ──────────────────────────────────────────────────────

    public static NidanResultResponse success(String sampleNumber, String analysisId, String accessionNumber,
            String testName, String resultValue, String resultType, String resultStatus, List<String> reflexTriggered,
            List<String> calculatedTriggered) {
        NidanResultResponse r = new NidanResultResponse();
        r.status = "success";
        r.message = "Result persisted successfully";
        r.sampleNumber = sampleNumber;
        r.analysisId = analysisId;
        r.accessionNumber = accessionNumber;
        r.testName = testName;
        r.resultValue = resultValue;
        r.resultType = resultType;
        r.resultStatus = resultStatus;
        r.reflexTriggered = reflexTriggered;
        r.calculatedTriggered = calculatedTriggered;
        return r;
    }

    public static NidanResultResponse error(String message) {
        NidanResultResponse r = new NidanResultResponse();
        r.status = "error";
        r.message = message;
        return r;
    }

    // ── getters ──────────────────────────────────────────────────────────────

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getSampleNumber() {
        return sampleNumber;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public String getTestName() {
        return testName;
    }

    public String getResultValue() {
        return resultValue;
    }

    public String getResultType() {
        return resultType;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public List<String> getReflexTriggered() {
        return reflexTriggered;
    }

    public List<String> getCalculatedTriggered() {
        return calculatedTriggered;
    }
}
