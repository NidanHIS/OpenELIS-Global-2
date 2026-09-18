package org.openelisglobal.analyzerimport.samplenumber;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.util.SampleNumberUtil;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Analyzer identity resolver. Never auto-picks among remaining candidates.
 * Accession fallback only when the candidate set is empty.
 */
@Service
public class AnalyzerSampleResolver {

    @Autowired
    private SampleService sampleService;
    @Autowired
    private SampleNumberCandidateFilter candidateFilter;

    public AnalyzerSampleResolveOutcome resolve(String identifier, String testId) {
        return resolve(identifier, testId, null);
    }

    public AnalyzerSampleResolveOutcome resolve(String identifier, String testId, Date asOf) {
        try {
            if (GenericValidator.isBlankOrNull(identifier)) {
                return AnalyzerSampleResolveOutcome.none();
            }
            String trimmed = identifier.trim();
            Map<String, Sample> byId = new LinkedHashMap<>();

            Sample exact = sampleService.getSampleBySampleNumberExact(trimmed);
            add(byId, exact);

            if (SampleNumberUtil.isDisplayFormat(trimmed)) {
                Date targetDate = (asOf != null) ? asOf : new Date();
                List<String> keys = SampleNumberUtil.storageKeysForLookback(trimmed,
                        SampleNumberUtil.LOOKBACK_CALENDAR_DAYS, targetDate);
                List<Sample> lookback = sampleService.findAutoSamplesByStoredNumbers(keys);
                if (lookback != null) {
                    Map<String, Sample> keyed = indexByStoredNumber(lookback);
                    for (String key : keys) {
                        add(byId, keyed.get(key));
                    }
                    for (Sample extra : lookback) {
                        add(byId, extra);
                    }
                }
            }

            List<Sample> filtered = applyFilter(new ArrayList<>(byId.values()), testId);
            if (filtered.size() == 1) {
                return AnalyzerSampleResolveOutcome.unique(filtered.get(0));
            }
            if (filtered.size() >= 2) {
                return AnalyzerSampleResolveOutcome.ambiguous(filtered);
            }

            Sample byAccession = sampleService.getSampleByAccessionNumber(trimmed);
            if (byAccession != null) {
                if (GenericValidator.isBlankOrNull(testId) || candidateFilter.isEligible(byAccession, testId)) {
                    return AnalyzerSampleResolveOutcome.unique(byAccession);
                }
            }
            return AnalyzerSampleResolveOutcome.none();
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "resolve", e.toString());
            return AnalyzerSampleResolveOutcome.none();
        }
    }

    private List<Sample> applyFilter(List<Sample> candidates, String testId) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        if (GenericValidator.isBlankOrNull(testId)) {
            return candidates;
        }
        List<Sample> kept = new ArrayList<>();
        for (Sample sample : candidates) {
            if (candidateFilter.isEligible(sample, testId)) {
                kept.add(sample);
            }
        }
        return kept;
    }

    private void add(Map<String, Sample> byId, Sample sample) {
        if (sample == null || GenericValidator.isBlankOrNull(sample.getId())) {
            return;
        }
        byId.putIfAbsent(sample.getId(), sample);
    }

    private Map<String, Sample> indexByStoredNumber(List<Sample> samples) {
        Map<String, Sample> keyed = new LinkedHashMap<>();
        for (Sample sample : samples) {
            if (sample != null && !GenericValidator.isBlankOrNull(sample.getSampleNumber())) {
                keyed.putIfAbsent(sample.getSampleNumber(), sample);
            }
        }
        return keyed;
    }
}
