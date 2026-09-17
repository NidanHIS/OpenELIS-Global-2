package org.openelisglobal.analyzerimport.samplenumber;

import java.util.ArrayList;
import java.util.List;

public class AnalyzerSampleAmbiguityCard {

    private Integer id;
    private String incomingIdentifier;
    private String analyzerId;
    private String analyzerTestCode;
    private String testId;
    private String testName;
    private String resultValue;
    private String resultUnits;
    private String resultDate;
    private List<AnalyzerSampleAmbiguityCandidate> candidates = new ArrayList<>();

    public Integer getId() {
        return id;
    }

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

    public String getResultDate() {
        return resultDate;
    }

    public void setResultDate(String resultDate) {
        this.resultDate = resultDate;
    }

    public List<AnalyzerSampleAmbiguityCandidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<AnalyzerSampleAmbiguityCandidate> candidates) {
        this.candidates = candidates != null ? candidates : new ArrayList<>();
    }
}
