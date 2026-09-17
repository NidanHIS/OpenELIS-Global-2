package org.openelisglobal.analyzerimport.samplenumber;

import java.util.ArrayList;
import java.util.List;

public class AnalyzerSampleAmbiguityCandidate {

    private String sampleId;
    private String accessionNumber;
    private String sampleNumber;
    private String patientName;
    private String patientId;
    private String orderDate;
    private List<String> orderedTests = new ArrayList<>();

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getSampleNumber() {
        return sampleNumber;
    }

    public void setSampleNumber(String sampleNumber) {
        this.sampleNumber = sampleNumber;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(String orderDate) {
        this.orderDate = orderDate;
    }

    public List<String> getOrderedTests() {
        return orderedTests;
    }

    public void setOrderedTests(List<String> orderedTests) {
        this.orderedTests = orderedTests != null ? orderedTests : new ArrayList<>();
    }
}
