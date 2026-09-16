package org.openelisglobal.analyzerimport.samplenumber;

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
 * Drops a lookback candidate when that incoming test already has a real result.
 * Holding occupancy is ignored. Fail-closed: any exception → not eligible.
 */
@Service
public class SampleNumberCandidateFilter {

    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private ResultService resultService;
    @Autowired
    private IStatusService statusService;

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
            }
            return foundOrdered;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "isEligible", e.toString());
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
