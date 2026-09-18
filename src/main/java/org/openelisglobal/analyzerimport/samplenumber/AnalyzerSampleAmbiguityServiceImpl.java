package org.openelisglobal.analyzerimport.samplenumber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analyzer.service.AnalyzerService;
import org.openelisglobal.analyzer.valueholder.Analyzer;
import org.openelisglobal.analyzerresults.service.AnalyzerResultsService;
import org.openelisglobal.analyzerresults.valueholder.AnalyzerResults;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.common.util.StringUtil;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.util.SampleNumberUtil;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Governing queue for 4-digit sample number ambiguity.
 *
 * <p>
 * When the analyzer resolver finds ≥2 candidates for a 4-digit identifier it
 * calls {@link #parkIncoming} instead of writing to {@code analyzer_results}. A
 * user then resolves via the governing UI (calls {@link #promoteToHolding} or
 * {@link #discard}).
 *
 * <p>
 * All methods are fail-closed: any exception → log + return safe default. Never
 * throws to callers.
 */
@Service
@Transactional
public class AnalyzerSampleAmbiguityServiceImpl implements AnalyzerSampleAmbiguityService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AnalyzerSampleAmbiguityDAO ambiguityDAO;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private AnalyzerResultsService analyzerResultsService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private SampleHumanService sampleHumanService;

    @Autowired
    private AnalyzerService analyzerService;

    @Autowired
    private TestService testService;

    @Autowired
    private SampleNumberCandidateFilter candidateFilter;

    // ── Park ──────────────────────────────────────────────────────────────────

    @Override
    public void parkIncoming(String incomingIdentifier, String analyzerId, String analyzerTestCode, String testId,
            String testName, String resultValue, String resultUnits, String resultType, Timestamp completeDate,
            List<Sample> candidates) {
        try {
            resultValue = StringUtil.capDecimalPlaces(resultValue, StringUtil.MAX_ANALYZER_DECIMAL_PLACES);
            if (GenericValidator.isBlankOrNull(incomingIdentifier) || candidates == null || candidates.size() < 2) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "parkIncoming", "invalid args — identifier="
                        + incomingIdentifier + " candidates=" + (candidates == null ? "null" : candidates.size()));
                return;
            }
            String numericAnalyzerId = resolveNumericAnalyzerId(analyzerId);
            Timestamp effectiveCompleteDate = completeDate != null ? completeDate
                    : new Timestamp(System.currentTimeMillis());

            // Build candidate IDs string
            StringBuilder ids = new StringBuilder();
            for (Sample s : candidates) {
                if (s != null && !GenericValidator.isBlankOrNull(s.getId())) {
                    if (ids.length() > 0)
                        ids.append(",");
                    ids.append(s.getId());
                }
            }
            String candidateIds = truncate(ids.toString(), 255);
            String candidateJson = buildCandidateJson(candidates);

            // Deduplication: Check if an identical PENDING record already exists on the
            // same incoming date
            List<AnalyzerSampleAmbiguity> existingPending = ambiguityDAO
                    .findPendingByIdentifierAndTest(incomingIdentifier, numericAnalyzerId, analyzerTestCode);
            String incomingDateStr = "";
            try {
                incomingDateStr = DateUtil.convertTimestampToStringDate(effectiveCompleteDate);
            } catch (Exception ignored) {
            }

            for (AnalyzerSampleAmbiguity existing : existingPending) {
                String existingDateStr = "";
                try {
                    if (existing.getCompleteDate() != null) {
                        existingDateStr = DateUtil.convertTimestampToStringDate(existing.getCompleteDate());
                    }
                } catch (Exception ignored) {
                }
                if (!GenericValidator.isBlankOrNull(incomingDateStr) && incomingDateStr.equals(existingDateStr)) {
                    // Update existing record in-place with latest result value, timestamp, and
                    // candidates
                    existing.setResultValue(resultValue);
                    existing.setResultUnits(resultUnits);
                    existing.setResultType(resultType);
                    existing.setCompleteDate(effectiveCompleteDate);
                    existing.setCandidateSampleIds(candidateIds);
                    existing.setCandidateJson(candidateJson);
                    ambiguityDAO.update(existing);
                    LogEvent.logInfo(this.getClass().getSimpleName(), "parkIncoming",
                            "Refreshed existing PENDING ambiguous result identifier=" + incomingIdentifier + " test="
                                    + analyzerTestCode + " rowId=" + existing.getId() + " newValue=" + resultValue);
                    return;
                }
            }

            AnalyzerSampleAmbiguity row = new AnalyzerSampleAmbiguity();
            row.setIncomingIdentifier(incomingIdentifier);
            row.setAnalyzerId(numericAnalyzerId);
            row.setAnalyzerTestCode(analyzerTestCode);
            row.setTestId(testId);
            row.setTestName(resolveFullTestName(testId, testName));
            row.setResultValue(resultValue);
            row.setResultUnits(resultUnits);
            row.setResultType(resultType);
            row.setCompleteDate(effectiveCompleteDate);
            row.setStatus(AnalyzerSampleAmbiguity.STATUS_PENDING);
            row.setCandidateSampleIds(candidateIds);
            row.setCandidateJson(candidateJson);

            ambiguityDAO.insert(row);
            LogEvent.logInfo(this.getClass().getSimpleName(), "parkIncoming", "Parked ambiguous result identifier="
                    + incomingIdentifier + " candidates=" + candidates.size() + " rowId=" + row.getId());
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "parkIncoming", e.toString());
            // Fail-closed: drop — never write holding with a guessed accession.
        }
    }

    // ── List / count ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AnalyzerSampleAmbiguityCard> listPending() {
        return listPending(0, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyzerSampleAmbiguityCard> listPending(int offset, int limit) {
        try {
            List<AnalyzerSampleAmbiguity> rows = ambiguityDAO.findByStatus(AnalyzerSampleAmbiguity.STATUS_PENDING,
                    offset, limit);
            List<AnalyzerSampleAmbiguityCard> cards = new ArrayList<>();
            for (AnalyzerSampleAmbiguity row : rows) {
                AnalyzerSampleAmbiguityCard card = toCard(row);
                if (card != null)
                    cards.add(card);
            }
            return cards;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "listPending", e.toString());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        try {
            return ambiguityDAO.findByStatus(AnalyzerSampleAmbiguity.STATUS_PENDING).size();
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "countPending", e.toString());
            return 0L;
        }
    }

    // ── Promote to holding ────────────────────────────────────────────────────

    /**
     * User chose {@code sampleId} for pending row {@code pendingId}. Validates the
     * sampleId is in the original candidate list, inserts one
     * {@link AnalyzerResults} holding row, marks RESOLVED. Returns false on any
     * failure — never writes with a guessed accession.
     */
    @Override
    public boolean promoteToHolding(Integer pendingId, String sampleId, String sysUserId) {
        try {
            if (pendingId == null || GenericValidator.isBlankOrNull(sampleId)) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "promoteToHolding",
                        "null pendingId or blank sampleId");
                return false;
            }
            AnalyzerSampleAmbiguity row = ambiguityDAO.get(pendingId).orElse(null);
            if (row == null) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "promoteToHolding",
                        "no row found for pendingId=" + pendingId);
                return false;
            }
            if (!AnalyzerSampleAmbiguity.STATUS_PENDING.equals(row.getStatus())) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "promoteToHolding",
                        "row " + pendingId + " not PENDING status=" + row.getStatus());
                return false;
            }
            if (!isCandidateId(row.getCandidateSampleIds(), sampleId)) {
                LogEvent.logError(this.getClass().getSimpleName(), "promoteToHolding",
                        "sampleId=" + sampleId + " not in candidates for pendingId=" + pendingId);
                return false;
            }
            Sample sample = sampleService.get(sampleId);
            if (sample == null || GenericValidator.isBlankOrNull(sample.getAccessionNumber())) {
                LogEvent.logError(this.getClass().getSimpleName(), "promoteToHolding",
                        "cannot load sample or blank accession for sampleId=" + sampleId);
                return false;
            }

            // Build AnalyzerResults holding row — strictly numeric analyzerId and full
            // testName
            String numericAnalyzerId = resolveNumericAnalyzerId(row.getAnalyzerId());
            String fullTestName = resolveFullTestName(row.getTestId(), row.getTestName());

            AnalyzerResults ar = new AnalyzerResults();
            ar.setAnalyzerId(numericAnalyzerId);
            ar.setAccessionNumber(sample.getAccessionNumber());
            ar.setTestName(fullTestName);
            ar.setTestId(row.getTestId());
            ar.setResult(StringUtil.capDecimalPlaces(row.getResultValue(), StringUtil.MAX_ANALYZER_DECIMAL_PLACES));
            ar.setUnits(row.getResultUnits());
            ar.setResultType(row.getResultType() != null ? row.getResultType() : "N");
            ar.setCompleteDate(row.getCompleteDate());
            ar.setIsControl(false);
            ar.setReadOnly(false);

            List<AnalyzerResults> toInsert = new ArrayList<>();
            toInsert.add(ar);
            analyzerResultsService.insertAnalyzerResults(toInsert, sysUserId);

            row.setStatus(AnalyzerSampleAmbiguity.STATUS_RESOLVED);
            row.setChosenSampleId(sampleId);
            row.setAnalyzerId(numericAnalyzerId);
            ambiguityDAO.update(row);

            LogEvent.logInfo(this.getClass().getSimpleName(), "promoteToHolding",
                    "Promoted pendingId=" + pendingId + " sampleId=" + sampleId + " accession="
                            + sample.getAccessionNumber() + " analyzerId=" + numericAnalyzerId + " testName="
                            + fullTestName);

            // Reactive Prune Cascade: Eject claimed sampleId from all other PENDING
            // ambiguity cards for this test
            pruneClaimedCandidateFromPeerCards(pendingId, sampleId, row.getTestId(), row.getAnalyzerTestCode());

            return true;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "promoteToHolding", e.toString());
            return false;
        }
    }

    private void pruneClaimedCandidateFromPeerCards(Integer resolvedPendingId, String chosenSampleId, String testId,
            String analyzerTestCode) {
        if (GenericValidator.isBlankOrNull(chosenSampleId)) {
            return;
        }
        try {
            List<AnalyzerSampleAmbiguity> allPending = ambiguityDAO
                    .findByStatus(AnalyzerSampleAmbiguity.STATUS_PENDING);
            for (AnalyzerSampleAmbiguity peer : allPending) {
                if (peer == null || peer.getId() == null || peer.getId().equals(resolvedPendingId)) {
                    continue;
                }
                // Match test by testId or analyzerTestCode
                boolean testMatches = false;
                if (!GenericValidator.isBlankOrNull(testId) && testId.equals(peer.getTestId())) {
                    testMatches = true;
                } else if (!GenericValidator.isBlankOrNull(analyzerTestCode)
                        && analyzerTestCode.equals(peer.getAnalyzerTestCode())) {
                    testMatches = true;
                }
                if (!testMatches) {
                    continue;
                }

                if (!isCandidateId(peer.getCandidateSampleIds(), chosenSampleId)) {
                    continue;
                }

                // Token-safe pruning of chosenSampleId
                String[] tokens = peer.getCandidateSampleIds().split(",");
                List<String> remainingIds = new ArrayList<>();
                for (String tok : tokens) {
                    String trimmed = tok.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(chosenSampleId)) {
                        remainingIds.add(trimmed);
                    }
                }

                String newCandidateIds = String.join(",", remainingIds);
                peer.setCandidateSampleIds(truncate(newCandidateIds, 255));

                // Rebuild candidate JSON
                List<Sample> remainingSamples = new ArrayList<>();
                for (String sId : remainingIds) {
                    try {
                        Sample s = sampleService.get(sId);
                        if (s != null) {
                            remainingSamples.add(s);
                        }
                    } catch (Exception ignored) {
                    }
                }
                peer.setCandidateJson(buildCandidateJson(remainingSamples));
                ambiguityDAO.update(peer);

                LogEvent.logInfo(this.getClass().getSimpleName(), "pruneClaimedCandidateFromPeerCards",
                        "Pruned claimed sampleId=" + chosenSampleId + " from peer pendingId=" + peer.getId()
                                + " remaining candidate count=" + remainingIds.size());
            }
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "pruneClaimedCandidateFromPeerCards",
                    "Error pruning candidate " + chosenSampleId + ": " + e.toString());
        }
    }

    // ── Discard ───────────────────────────────────────────────────────────────

    @Override
    public boolean discard(Integer pendingId, String sysUserId) {
        try {
            if (pendingId == null)
                return false;
            AnalyzerSampleAmbiguity row = ambiguityDAO.get(pendingId).orElse(null);
            if (row == null) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "discard", "no row for pendingId=" + pendingId);
                return false;
            }
            if (!AnalyzerSampleAmbiguity.STATUS_PENDING.equals(row.getStatus())) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "discard",
                        "row " + pendingId + " is not PENDING — status=" + row.getStatus());
                return false;
            }
            row.setStatus(AnalyzerSampleAmbiguity.STATUS_DISCARDED);
            ambiguityDAO.update(row);
            LogEvent.logInfo(this.getClass().getSimpleName(), "discard", "Discarded pendingId=" + pendingId);
            return true;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "discard", e.toString());
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AnalyzerSampleAmbiguityCard toCard(AnalyzerSampleAmbiguity row) {
        try {
            AnalyzerSampleAmbiguityCard card = new AnalyzerSampleAmbiguityCard();
            card.setId(row.getId());
            card.setIncomingIdentifier(row.getIncomingIdentifier());
            card.setAnalyzerId(resolveAnalyzerDisplayName(row.getAnalyzerId()));
            card.setAnalyzerTestCode(row.getAnalyzerTestCode());
            card.setTestId(row.getTestId());
            card.setTestName(resolveFullTestName(row.getTestId(), row.getTestName()));
            card.setResultValue(row.getResultValue());
            card.setResultUnits(row.getResultUnits());
            String resDate = "";
            try {
                if (row.getCompleteDate() != null) {
                    resDate = DateUtil.convertTimestampToStringDate(row.getCompleteDate());
                } else {
                    resDate = DateUtil.convertSqlDateToStringDate(DateUtil.getNowAsSqlDate());
                }
            } catch (Exception e) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "toCard",
                        "failed formatting resultDate: " + e.getMessage());
            }
            card.setResultDate(resDate);
            card.setCandidates(buildCandidateList(row.getCandidateSampleIds(), row.getTestId()));
            return card;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "toCard", e.toString());
            return null;
        }
    }

    private String resolveNumericAnalyzerId(String rawAnalyzerId) {
        if (!GenericValidator.isBlankOrNull(rawAnalyzerId)) {
            String trimmed = rawAnalyzerId.trim();
            if (trimmed.matches("^\\d+$")) {
                return trimmed;
            }
            try {
                java.util.Optional<Analyzer> byName = analyzerService.getByName(trimmed);
                if (byName.isPresent()) {
                    return byName.get().getId();
                }
                List<Analyzer> list = analyzerService.getAll();
                if (list != null && !list.isEmpty()) {
                    String normTarget = normalize(trimmed);

                    for (Analyzer a : list) {
                        if (a.getName() != null && normalize(a.getName()).equals(normTarget)) {
                            return a.getId();
                        }
                    }
                    // 2. Substring / contains match
                    for (Analyzer a : list) {
                        if (a.getName() != null && (normTarget.contains(normalize(a.getName()))
                                || normalize(a.getName()).contains(normTarget))) {
                            return a.getId();
                        }
                    }
                    // 3. Match against AnalyzerType plugin class name or type name
                    for (Analyzer a : list) {
                        if (a.getAnalyzerType() != null) {
                            String pluginClass = a.getAnalyzerType().getPluginClassName();
                            String typeName = a.getAnalyzerType().getName();
                            if ((pluginClass != null && (normTarget.contains(normalize(pluginClass))
                                    || normalize(pluginClass).contains(normTarget)))
                                    || (typeName != null && normalize(typeName).equals(normTarget))) {
                                return a.getId();
                            }
                        }
                    }
                    return list.get(0).getId();
                }
            } catch (Exception e) {
                LogEvent.logError(this.getClass().getSimpleName(), "resolveNumericAnalyzerId", e.toString());
            }
        }
        // Fallback: first available analyzer in the database
        try {
            List<Analyzer> list = analyzerService.getAll();
            if (list != null && !list.isEmpty()) {
                return list.get(0).getId();
            }
        } catch (Exception ignored) {
        }
        return "1";
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String resolveAnalyzerDisplayName(String rawAnalyzerId) {
        if (!GenericValidator.isBlankOrNull(rawAnalyzerId)) {
            String trimmed = rawAnalyzerId.trim();
            if (trimmed.matches("^\\d+$")) {
                try {
                    Analyzer a = analyzerService.get(trimmed);
                    if (a != null && !GenericValidator.isBlankOrNull(a.getName())) {
                        return a.getName();
                    }
                } catch (Exception ignored) {
                }
            }
            return trimmed;
        }
        return "";
    }

    private String resolveFullTestName(String testId, String fallbackName) {
        if (!GenericValidator.isBlankOrNull(testId)) {
            try {
                String localized = org.openelisglobal.test.service.TestServiceImpl.getUserLocalizedTestName(testId);
                if (!GenericValidator.isBlankOrNull(localized)) {
                    return localized;
                }
                Test test = new Test();
                test.setId(testId);
                testService.getData(test);
                if (test.getLocalizedTestName() != null
                        && !GenericValidator.isBlankOrNull(test.getLocalizedTestName().getLocalizedValue())) {
                    return test.getLocalizedTestName().getLocalizedValue();
                }
                if (!GenericValidator.isBlankOrNull(test.getName())) {
                    return test.getName();
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackName;
    }

    private List<AnalyzerSampleAmbiguityCandidate> buildCandidateList(String ids) {
        return buildCandidateList(ids, null);
    }

    private List<AnalyzerSampleAmbiguityCandidate> buildCandidateList(String ids, String testId) {
        List<AnalyzerSampleAmbiguityCandidate> list = new ArrayList<>();
        if (GenericValidator.isBlankOrNull(ids))
            return list;
        for (String id : ids.split(",")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty())
                continue;
            try {
                Sample sample = sampleService.get(trimmed);
                if (sample == null)
                    continue;
                // Live safety filter: verify candidate is still eligible (not already in
                // holding or finalized)
                if (candidateFilter != null && !GenericValidator.isBlankOrNull(testId)
                        && !candidateFilter.isEligible(sample, testId)) {
                    LogEvent.logInfo(this.getClass().getSimpleName(), "buildCandidateList",
                            "Filtered out ineligible sample " + sample.getAccessionNumber() + " for test " + testId);
                    continue;
                }
                AnalyzerSampleAmbiguityCandidate c = new AnalyzerSampleAmbiguityCandidate();
                c.setSampleId(sample.getId());
                c.setAccessionNumber(sample.getAccessionNumber());
                c.setSampleNumber(SampleNumberUtil.toDisplay(sample.getSampleNumber()));
                // Order date matching Dashboard backlog
                String orderDate = sample.getEnteredDateForDisplay();
                if (GenericValidator.isBlankOrNull(orderDate) && sample.getEnteredDate() != null) {
                    try {
                        orderDate = DateUtil.convertSqlDateToStringDate(sample.getEnteredDate());
                    } catch (Exception ignored) {
                    }
                }
                c.setOrderDate(orderDate != null ? orderDate : "");
                // Patient
                try {
                    Patient patient = sampleHumanService.getPatientForSample(sample);
                    if (patient != null) {
                        c.setPatientId(patient.getNationalId());
                        if (patient.getPerson() != null) {
                            String name = (nvl(patient.getPerson().getFirstName()) + " "
                                    + nvl(patient.getPerson().getLastName())).trim();
                            c.setPatientName(name);
                        }
                    }
                } catch (Exception pe) {
                    LogEvent.logWarn(this.getClass().getSimpleName(), "buildCandidateList",
                            "patient load failed for " + trimmed + ": " + pe.getMessage());
                }
                // Ordered tests
                try {
                    List<Analysis> analyses = analysisService.getAnalysesBySampleId(trimmed);
                    List<String> testNames = new ArrayList<>();
                    if (analyses != null) {
                        for (Analysis a : analyses) {
                            if (a != null && a.getTest() != null
                                    && !GenericValidator.isBlankOrNull(a.getTest().getName())) {
                                testNames.add(a.getTest().getName());
                            }
                        }
                    }
                    c.setOrderedTests(testNames);
                } catch (Exception ae) {
                    LogEvent.logWarn(this.getClass().getSimpleName(), "buildCandidateList",
                            "analysis load failed for " + trimmed + ": " + ae.getMessage());
                }
                list.add(c);
            } catch (Exception e) {
                LogEvent.logWarn(this.getClass().getSimpleName(), "buildCandidateList",
                        "skipping candidate id=" + trimmed + ": " + e.getMessage());
            }
        }
        return list;
    }

    private String buildCandidateJson(List<Sample> candidates) {
        try {
            List<AnalyzerSampleAmbiguityCandidate> list = new ArrayList<>();
            for (Sample s : candidates) {
                if (s == null || GenericValidator.isBlankOrNull(s.getId()))
                    continue;
                AnalyzerSampleAmbiguityCandidate c = new AnalyzerSampleAmbiguityCandidate();
                c.setSampleId(s.getId());
                c.setAccessionNumber(s.getAccessionNumber());
                c.setSampleNumber(SampleNumberUtil.toDisplay(s.getSampleNumber()));
                String orderDate = s.getEnteredDateForDisplay();
                if (GenericValidator.isBlankOrNull(orderDate) && s.getEnteredDate() != null) {
                    try {
                        orderDate = DateUtil.convertSqlDateToStringDate(s.getEnteredDate());
                    } catch (Exception ignored) {
                    }
                }
                c.setOrderDate(orderDate != null ? orderDate : "");
                list.add(c);
            }
            return JSON.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "buildCandidateJson", e.getMessage());
            return "[]";
        }
    }

    private boolean isCandidateId(String candidateSampleIds, String sampleId) {
        if (GenericValidator.isBlankOrNull(candidateSampleIds) || GenericValidator.isBlankOrNull(sampleId))
            return false;
        for (String id : candidateSampleIds.split(",")) {
            if (sampleId.trim().equals(id.trim()))
                return true;
        }
        return false;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    private String truncate(String s, int max) {
        if (s == null)
            return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
