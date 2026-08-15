package org.openelisglobal.nidanresult;

/**
 * Single row returned by GET /rest/nidan/result/tests.
 *
 * <p>
 * Each item represents one test configuration available in OpenELIS. Callers
 * can use {@code testId} when interpreting results or {@code testName} for
 * display purposes.
 */
public class NidanTestItem {

    private String testId;
    private String testName;
    private String shortName;
    private String description;
    private boolean orderable;
    private boolean active;

    // ── constructor ──────────────────────────────────────────────────────────

    public NidanTestItem() {
    }

    public NidanTestItem(String testId, String testName, String shortName, String description, boolean orderable,
            boolean active) {
        this.testId = testId;
        this.testName = testName;
        this.shortName = shortName;
        this.description = description;
        this.orderable = orderable;
        this.active = active;
    }

    // ── getters & setters ────────────────────────────────────────────────────

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

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isOrderable() {
        return orderable;
    }

    public void setOrderable(boolean orderable) {
        this.orderable = orderable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}