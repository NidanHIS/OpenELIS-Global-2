package org.openelisglobal.nidanresult;

/**
 * Single row returned by GET /rest/nidan/result/pending.
 *
 * <p>
 * Each item represents one analysis (test) that is in a pending state for the
 * requested sample number. The caller uses {@code analysisId} as the key when
 * posting a result via POST /rest/nidan/result/entry.
 */
public class NidanPendingResultItem {

    private String analysisId;
    private String testId;
    private String testName;
    private String sampleNumber;
    private String accessionNumber;
    private String analysisStatus;
    private String patientId;
    private String patientFirstName;
    private String patientLastName;
    private String collectionDate;
    private String enteredDate;

    // ── constructor ──────────────────────────────────────────────────────────

    public NidanPendingResultItem() {
    }

    public NidanPendingResultItem(String analysisId, String testId, String testName, String sampleNumber,
            String accessionNumber, String analysisStatus, String patientId, String patientFirstName,
            String patientLastName, String collectionDate, String enteredDate) {
        this.analysisId = analysisId;
        this.testId = testId;
        this.testName = testName;
        this.sampleNumber = sampleNumber;
        this.accessionNumber = accessionNumber;
        this.analysisStatus = analysisStatus;
        this.patientId = patientId;
        this.patientFirstName = patientFirstName;
        this.patientLastName = patientLastName;
        this.collectionDate = collectionDate;
        this.enteredDate = enteredDate;
    }

    // ── getters & setters ────────────────────────────────────────────────────

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getSampleNumber() {
        return sampleNumber;
    }

    public void setSampleNumber(String sampleNumber) {
        this.sampleNumber = sampleNumber;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(String analysisStatus) {
        this.analysisStatus = analysisStatus;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientFirstName() {
        return patientFirstName;
    }

    public void setPatientFirstName(String patientFirstName) {
        this.patientFirstName = patientFirstName;
    }

    public String getPatientLastName() {
        return patientLastName;
    }

    public void setPatientLastName(String patientLastName) {
        this.patientLastName = patientLastName;
    }

    public String getCollectionDate() {
        return collectionDate;
    }

    public void setCollectionDate(String collectionDate) {
        this.collectionDate = collectionDate;
    }

    public String getEnteredDate() {
        return enteredDate;
    }

    public void setEnteredDate(String enteredDate) {
        this.enteredDate = enteredDate;
    }
}
