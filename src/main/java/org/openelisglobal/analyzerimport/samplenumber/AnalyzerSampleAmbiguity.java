package org.openelisglobal.analyzerimport.samplenumber;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "analyzer_sample_ambiguity")
public class AnalyzerSampleAmbiguity extends BaseObject<Integer> {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_DISCARDED = "DISCARDED";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analyzer_sample_ambiguity_generator")
    @SequenceGenerator(name = "analyzer_sample_ambiguity_generator", sequenceName = "analyzer_sample_ambiguity_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "incoming_identifier", length = 40)
    private String incomingIdentifier;

    @Column(name = "analyzer_id", length = 20)
    private String analyzerId;

    @Column(name = "analyzer_test_code", length = 80)
    private String analyzerTestCode;

    @Column(name = "test_id", length = 20)
    private String testId;

    @Column(name = "test_name", length = 255)
    private String testName;

    @Column(name = "result_value", length = 255)
    private String resultValue;

    @Column(name = "result_units", length = 80)
    private String resultUnits;

    @Column(name = "result_type", length = 8)
    private String resultType;

    @Column(name = "complete_date")
    private Timestamp completeDate;

    @Column(name = "candidate_sample_ids", length = 255)
    private String candidateSampleIds;

    @Column(name = "candidate_json", columnDefinition = "TEXT")
    private String candidateJson;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "chosen_sample_id", length = 20)
    private String chosenSampleId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public String getIncomingIdentifier() {
        return incomingIdentifier;
    }

    public void setIncomingIdentifier(String incomingIdentifier) {
        this.incomingIdentifier = incomingIdentifier;
    }

    public String getAnalyzerId() {
        return analyzerId;
    }

    public void setAnalyzerId(String analyzerId) {
        this.analyzerId = analyzerId;
    }

    public String getAnalyzerTestCode() {
        return analyzerTestCode;
    }

    public void setAnalyzerTestCode(String analyzerTestCode) {
        this.analyzerTestCode = analyzerTestCode;
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

    public String getResultValue() {
        return resultValue;
    }

    public void setResultValue(String resultValue) {
        this.resultValue = resultValue;
    }

    public String getResultUnits() {
        return resultUnits;
    }

    public void setResultUnits(String resultUnits) {
        this.resultUnits = resultUnits;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public Timestamp getCompleteDate() {
        return completeDate;
    }

    public void setCompleteDate(Timestamp completeDate) {
        this.completeDate = completeDate;
    }

    public String getCandidateSampleIds() {
        return candidateSampleIds;
    }

    public void setCandidateSampleIds(String candidateSampleIds) {
        this.candidateSampleIds = candidateSampleIds;
    }

    public String getCandidateJson() {
        return candidateJson;
    }

    public void setCandidateJson(String candidateJson) {
        this.candidateJson = candidateJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChosenSampleId() {
        return chosenSampleId;
    }

    public void setChosenSampleId(String chosenSampleId) {
        this.chosenSampleId = chosenSampleId;
    }
}
