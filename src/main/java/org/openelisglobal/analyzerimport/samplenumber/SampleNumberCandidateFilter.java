package org.openelisglobal.analyzerimport.samplenumber;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Drops a lookback candidate when that incoming test already has a real result
 * or is already occupying analyzer_results holding. Fail-closed: any exception
 * → not eligible.
 */
@Service
public class SampleNumberCandidateFilter {

    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private ResultService resultService;
    @Autowired
    private IStatusService statusService;
    @PersistenceContext
    private EntityManager entityManager;

    public boolean isEligible(Sample sample, String testId) {
        try {
            if (sample == null || GenericValidator.isBlankOrNull(sample.getId())
                    || GenericValidator.isBlankOrNull(testId)) {
                return false;
            }
            List<Analysis> analyses = analysisService.getAnalysesBySampleId(sample.getId());
            if (analyses == null || analyses.isEmpty()) {
                return false;
            }
            String canceledId = statusService.getStatusID(AnalysisStatus.Canceled);
            String acceptedId = statusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
            String finalizedId = statusService.getStatusID(AnalysisStatus.Finalized);

            boolean foundOrdered = false;
            for (Analysis analysis : analyses) {
                if (analysis == null || analysis.getTest() == null
                        || GenericValidator.isBlankOrNull(analysis.getTest().getId())) {
                    continue;
                }
                if (!testId.equals(analysis.getTest().getId())) {
                    continue;
                }
                if (canceledId != null && canceledId.equals(analysis.getStatusId())) {
                    continue;
                }
                foundOrdered = true;
                if ((acceptedId != null && acceptedId.equals(analysis.getStatusId()))
                        || (finalizedId != null && finalizedId.equals(analysis.getStatusId()))) {
                    return false;
                }
                if (hasNonBlankResult(analysis)) {
                    return false;
                }
                // Check Stage 2: Is this accession already occupying analyzer_results holding
                // for this test?
                String testName = (analysis.getTest().getLocalizedTestName() != null)
                        ? analysis.getTest().getLocalizedTestName().getLocalizedValue()
                        : analysis.getTest().getName();
                if (isAccessionInHolding(sample.getAccessionNumber(), testId, testName)) {
                    LogEvent.logInfo(this.getClass().getSimpleName(), "isEligible",
                            "Accession " + sample.getAccessionNumber()
                                    + " already has active result in analyzer holding for test " + testId);
                    return false;
                }
            }
            return foundOrdered;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "isEligible", e.toString());
            return false;
        }
    }

    private boolean isAccessionInHolding(String accessionNumber, String testId, String testName) {
        if (GenericValidator.isBlankOrNull(accessionNumber) || entityManager == null) {
            return false;
        }
        try {
            String sql = "SELECT count(1) FROM clinlims.analyzer_results "
                    + "WHERE accession_number = :accessionNumber " + "AND (test_id = :testId OR test_name = :testName) "
                    + "AND (status_id != 3 OR status_id IS NULL)";
            Long numericTestId = -1L;
            if (!GenericValidator.isBlankOrNull(testId)) {
                try {
                    numericTestId = Long.parseLong(testId);
                } catch (Exception ignored) {
                }
            }
            Number count = (Number) entityManager.createNativeQuery(sql)
                    .setParameter("accessionNumber", accessionNumber).setParameter("testId", numericTestId)
                    .setParameter("testName", testName != null ? testName : "").getSingleResult();
            return count != null && count.longValue() > 0;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "isAccessionInHolding", e.toString());
            return false;
        }
    }

    private boolean hasNonBlankResult(Analysis analysis) {
        try {
            List<Result> results = resultService.getResultsByAnalysis(analysis);
            if (results == null) {
                return false;
            }
            for (Result result : results) {
                if (result != null && !GenericValidator.isBlankOrNull(result.getValue())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "hasNonBlankResult", e.toString());
            return true;
        }
        return false;
    }
}
