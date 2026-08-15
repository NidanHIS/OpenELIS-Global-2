package org.openelisglobal.nidanresult;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Inbound payload for POST /rest/nidan/result/entry.
 *
 * <p>
 * Callers identify the sample via {@code sampleNumber} (the NIDAN daily
 * counter, e.g. {@code 1508-0001} or a custom alphanumeric). {@code analysisId}
 * pins the exact test within that sample. {@code resultValue} carries the raw
 * value; {@code resultType} is one of N (numeric), D (dictionary), R (remark) —
 * defaults to N if absent.
 *
 * <p>
 * All unknown JSON fields are ignored so future callers can include extra
 * metadata without breaking this endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NidanResultRequest {

    /** NIDAN sample number — used to look up the Sample. Required. */
    private String sampleNumber;

    /**
     * OpenELIS analysis ID — identifies the specific test row to post the result
     * against. Required.
     */
    private String analysisId;

    /**
     * The result value to persist. For numeric (N): plain number string. For
     * dictionary (D): dictionary entry text. For remark (R): free text. Required.
     */
    private String resultValue;

    /**
     * Result type code: {@code N} = numeric, {@code D} = dictionary, {@code R} =
     * remark. Optional — defaults to {@code N} when absent or blank.
     */
    private String resultType;

    /**
     * Test/collection date in {@code DD/MM/YYYY} or {@code MM/DD/YYYY} format as
     * expected by the existing date utilities. Optional — defaults to today.
     */
    private String testDate;

    /** Optional internal note to attach to this result. */
    private String note;

    // ── getters & setters ────────────────────────────────────────────────────

    public String getSampleNumber() {
        return sampleNumber;
    }

    public void setSampleNumber(String sampleNumber) {
        this.sampleNumber = sampleNumber;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public String getResultValue() {
        return resultValue;
    }

    public void setResultValue(String resultValue) {
        this.resultValue = resultValue;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public String getTestDate() {
        return testDate;
    }

    public void setTestDate(String testDate) {
        this.testDate = testDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
