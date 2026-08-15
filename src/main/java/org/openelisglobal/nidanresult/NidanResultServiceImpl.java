package org.openelisglobal.nidanresult;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.validator.GenericValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.registration.ResultUpdateRegister;
import org.openelisglobal.common.services.registration.interfaces.IResultUpdate;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.dataexchange.fhir.exception.FhirPersistanceException;
import org.openelisglobal.dataexchange.fhir.exception.FhirTransformationException;
import org.openelisglobal.dataexchange.fhir.service.FhirTransformService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.result.action.util.ResultUtil;
import org.openelisglobal.result.action.util.ResultsUpdateDataSet;
import org.openelisglobal.result.service.LogbookResultsPersistService;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.resultlimit.service.ResultLimitService;
import org.openelisglobal.resultlimits.valueholder.ResultLimit;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link NidanResultService}.
 *
 * <h2>Design contract</h2>
 * <ul>
 * <li>Zero direct DAO calls. All persistence goes through
 * {@link LogbookResultsPersistService#persistDataSet} — the exact same
 * transactional pipeline the OpenELIS-Global-2 UI uses.</li>
 * <li>Zero modifications to any existing class.</li>
 * <li>Every error surface throws {@link NidanResultException} with a
 * descriptive message and an appropriate {@code Kind}.</li>
 * </ul>
 *
 * <h2>TestResultItem population</h2> The fields that must be set for the
 * pipeline to work correctly are documented inline. The most critical is
 * {@code resultLimitId}: without it, {@link ResultUtil#getStatusForTestResult}
 * cannot decide between Finalized and TechnicalAcceptance, and will default to
 * TechnicalAcceptance for every result. We populate it from
 * {@link ResultLimitService#getResultLimitForAnalysis} so the normal-range
 * logic (including the {@code alwaysValidate} flag) is applied identically to
 * the UI.
 */
@Service
public class NidanResultServiceImpl implements NidanResultService {

    private static final Logger LOG = LogManager.getLogger(NidanResultServiceImpl.class);
    private static final String LOG_PREFIX = "[NIDAN-RESULT]";

    // ── injected collaborators ───────────────────────────────────────────────

    @Autowired
    private SampleService sampleService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private TestService testService;

    @Autowired
    private ResultLimitService resultLimitService;

    @Autowired
    private IStatusService statusService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private LogbookResultsPersistService logbookPersistService;

    @Autowired
    private FhirTransformService fhirTransformService;

    // ── NidanResultService ───────────────────────────────────────────────────

    @Override
    public List<NidanPendingResultItem> getPendingResults(String sampleNumber) throws NidanResultException {

        // ── 1. validate input ────────────────────────────────────────────────
        if (GenericValidator.isBlankOrNull(sampleNumber)) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST, "sampleNumber is required");
        }

        // ── 2. resolve sample ────────────────────────────────────────────────
        Sample sample;
        try {
            sample = sampleService.getSampleBySampleNumber(sampleNumber.trim());
        } catch (Exception e) {
            LOG.error("{} error looking up sample for sampleNumber={}: {}", LOG_PREFIX, sampleNumber, e.getMessage(),
                    e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error looking up sample: " + e.getMessage(), e);
        }

        if (sample == null) {
            throw new NidanResultException(NidanResultException.Kind.NOT_FOUND,
                    "No sample found for sampleNumber: " + sampleNumber);
        }

        // ── 3. resolve patient (best-effort — null patient is fine) ──────────
        Patient patient = null;
        try {
            patient = sampleService.getPatient(sample);
        } catch (Exception e) {
            // non-fatal — we log and continue with null patient
            LOG.warn("{} could not resolve patient for sample {}: {}", LOG_PREFIX, sample.getAccessionNumber(),
                    e.getMessage());
        }

        // ── 4. collect all analyses for this sample ──────────────────────────
        List<Analysis> analyses;
        try {
            analyses = sampleService.getAnalysis(sample);
        } catch (Exception e) {
            LOG.error("{} error fetching analyses for sample {}: {}", LOG_PREFIX, sample.getAccessionNumber(),
                    e.getMessage(), e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error fetching analyses: " + e.getMessage(), e);
        }

        if (analyses == null || analyses.isEmpty()) {
            return Collections.emptyList();
        }

        // ── 5. status IDs for pending states ─────────────────────────────────
        String notStartedId = statusService.getStatusID(AnalysisStatus.NotStarted);
        String techAcceptanceId = statusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
        String canceledId = statusService.getStatusID(AnalysisStatus.Canceled);

        // ── 6. build response list ───────────────────────────────────────────
        List<NidanPendingResultItem> items = new ArrayList<>();

        for (Analysis analysis : analyses) {
            try {
                String statusId = analysis.getStatusId();

                // skip canceled and any status that is not pending
                if (canceledId.equals(statusId)) {
                    continue;
                }
                if (!notStartedId.equals(statusId) && !techAcceptanceId.equals(statusId)) {
                    continue;
                }

                String testId = "";
                String testName = "";
                try {
                    if (analysis.getTest() != null) {
                        testId = analysis.getTest().getId();
                        testName = analysisService.getTestDisplayName(analysis);
                    }
                } catch (Exception e) {
                    LOG.warn("{} could not resolve test name for analysisId={}: {}", LOG_PREFIX, analysis.getId(),
                            e.getMessage());
                }

                String patientId = "";
                String firstName = "";
                String lastName = "";
                if (patient != null) {
                    try {
                        patientId = patient.getId();
                        if (patient.getPerson() != null) {
                            firstName = nullSafe(patient.getPerson().getFirstName());
                            lastName = nullSafe(patient.getPerson().getLastName());
                        }
                    } catch (Exception e) {
                        LOG.warn("{} could not read patient person data: {}", LOG_PREFIX, e.getMessage());
                    }
                }

                String collectionDate = "";
                try {
                    collectionDate = DateUtil.convertTimestampToStringDate(sample.getCollectionDate());
                } catch (Exception e) {
                    LOG.warn("{} could not convert collectionDate: {}", LOG_PREFIX, e.getMessage());
                }

                String enteredDate = "";
                try {
                    enteredDate = DateUtil.convertSqlDateToStringDate(sample.getEnteredDate());
                } catch (Exception e) {
                    LOG.warn("{} could not convert enteredDate: {}", LOG_PREFIX, e.getMessage());
                }

                String humanStatus = statusIdToName(statusId, notStartedId, techAcceptanceId);

                items.add(new NidanPendingResultItem(analysis.getId(), testId, testName, sampleNumber,
                        sample.getAccessionNumber(), humanStatus, patientId, firstName, lastName, collectionDate,
                        enteredDate));

            } catch (Exception e) {
                // one bad analysis should not kill the whole list
                LOG.warn("{} skipping analysisId={} due to error: {}", LOG_PREFIX, analysis.getId(), e.getMessage());
            }
        }

        return items;
    }

    @Override
    public List<NidanTestItem> getAllTests(boolean orderableOnly) throws NidanResultException {

        try {
            List<Test> tests;
            if (orderableOnly) {
                // Get only orderable tests - these are tests that can be requested
                tests = testService.getAllActiveOrderableTests();
            } else {
                // Get all active tests regardless of orderable status
                tests = testService.getAllActiveTests(true); // true = onlyTestsFullySetup
            }

            if (tests == null) {
                return Collections.emptyList();
            }

            List<NidanTestItem> items = new ArrayList<>();
            for (Test test : tests) {
                try {
                    String testId = test.getId();
                    String testName = nullSafe(test.getName());
                    String shortName = nullSafe(test.getLabelName());
                    String description = nullSafe(test.getDescription());
                    boolean orderable = test.getOrderable() != null && test.getOrderable();
                    boolean active = test.isActive();

                    items.add(new NidanTestItem(testId, testName, shortName, description, orderable, active));

                } catch (Exception e) {
                    // Skip this test if there's an error reading its properties
                    LOG.warn("{} skipping testId={} due to error: {}", LOG_PREFIX, test.getId(), e.getMessage());
                }
            }

            return items;

        } catch (Exception e) {
            LOG.error("{} error fetching tests (orderableOnly={}): {}", LOG_PREFIX, orderableOnly, e.getMessage(), e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error fetching tests: " + e.getMessage(), e);
        }
    }

    @Override
    public NidanResultResponse submitResult(NidanResultRequest request, String sysUserId,
            HttpServletRequest httpRequest) throws NidanResultException {

        // ── 1. validate required fields ──────────────────────────────────────
        validateSubmitRequest(request, sysUserId);

        // ── 2. resolve sample via sampleNumber ───────────────────────────────
        Sample sample = resolveSample(request.getSampleNumber());

        // ── 3. load and validate the analysis ────────────────────────────────
        Analysis analysis = resolveAnalysis(request.getAnalysisId(), sample);

        // ── 4. resolve result limit for this analysis ────────────────────────
        // This is the critical field — it drives Finalized vs TechnicalAcceptance.
        ResultLimit resultLimit = resolveResultLimit(analysis);

        // ── 6. normalise resultType ───────────────────────────────────────────
        String resultType = normaliseResultType(request.getResultType());

        // ── 7. resolve testDate ───────────────────────────────────────────────
        String testDate = resolveTestDate(request.getTestDate());

        // ── 8. build a fully-populated TestResultItem ─────────────────────────
        TestResultItem item = buildTestResultItem(request, analysis, sample, resultLimit, resultType, testDate,
                sysUserId);

        // ── 9. build the dataset and run validation ───────────────────────────
        ResultsUpdateDataSet dataSet = new ResultsUpdateDataSet(sysUserId);
        dataSet.filterModifiedItems(Collections.singletonList(item));

        org.springframework.validation.Errors errors = dataSet.validateModifiedItems();
        if (errors.hasErrors()) {
            String msg = errors.getAllErrors().stream()
                    .map(e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : e.getCode())
                    .collect(Collectors.joining("; "));
            LOG.warn("{} validateModifiedItems rejected: {}", LOG_PREFIX, msg);
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST, "Result validation failed: " + msg);
        }

        // ── 10. pass the live servlet request to ResultUtil ───────────────────
        // ResultUtil.createResultsFromItems calls
        // ControllerUtills.getSysUserId(request)
        // internally. For Basic Auth calls the session lookup returns null (no
        // session),
        // then falls through to the SecurityContext strategy which succeeds because
        // Spring Security already authenticated the principal on this thread.
        // Passing null here would NPE on request.getSession() before the fallback runs.

        // ── 11. run result + analysis creation (mirrors UI pipeline) ──────────
        boolean alwaysValidate = ConfigurationProperties.getInstance()
                .isPropertyValueEqual(Property.ALWAYS_VALIDATE_RESULTS, "true");
        boolean useTechnicianName = ConfigurationProperties.getInstance()
                .isPropertyValueEqual(Property.resultTechnicianName, "true");
        boolean supportReferrals = false; // bridge never submits referrals
        String statusRuleSet = ConfigurationProperties.getInstance().getPropertyValueUpperCase(Property.StatusRules);

        try {
            ResultUtil.createResultsFromItems(dataSet, supportReferrals, alwaysValidate, useTechnicianName,
                    statusRuleSet, httpRequest);
            ResultUtil.createAnalysisOnlyUpdates(dataSet, httpRequest);
        } catch (Exception e) {
            LOG.error("{} error in ResultUtil pipeline for analysisId={}: {}", LOG_PREFIX, request.getAnalysisId(),
                    e.getMessage(), e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error preparing result dataset: " + e.getMessage(), e);
        }

        // ── 12. persist (transactional) ───────────────────────────────────────
        List<Analysis> reflexAnalyses;
        List<IResultUpdate> updaters = ResultUpdateRegister.getRegisteredUpdaters();
        try {
            reflexAnalyses = logbookPersistService.persistDataSet(dataSet, updaters, sysUserId);
        } catch (Exception e) {
            LOG.error("{} persistDataSet failed for analysisId={}: {}", LOG_PREFIX, request.getAnalysisId(),
                    e.getMessage(), e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error persisting result: " + e.getMessage(), e);
        }

        // ── 13. post-transactional updaters ───────────────────────────────────
        for (IResultUpdate updater : updaters) {
            try {
                updater.postTransactionalCommitUpdate(dataSet);
            } catch (Exception e) {
                // non-fatal — log and continue so we don't roll back a successful persist
                LOG.error("{} postTransactionalCommitUpdate error ({}): {}", LOG_PREFIX,
                        updater.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        // ── 14. FHIR transform (non-fatal) ────────────────────────────────────
        try {
            fhirTransformService.transformPersistResultsEntryFhirObjects(dataSet);
        } catch (FhirTransformationException | FhirPersistanceException e) {
            LOG.error("{} FHIR transform failed for analysisId={}: {}", LOG_PREFIX, request.getAnalysisId(),
                    e.getMessage(), e);
            // non-fatal — result is already persisted; FHIR sync can be retried
        } catch (Exception e) {
            LOG.error("{} unexpected FHIR error for analysisId={}: {}", LOG_PREFIX, request.getAnalysisId(),
                    e.getMessage(), e);
        }

        // ── 15. resolve actual status written to the analysis ─────────────────
        String writtenStatus = resolveWrittenStatus(request.getAnalysisId());

        // ── 16. split reflex / calculated from the pipeline return value ──────
        List<String> reflexTriggered = new ArrayList<>();
        List<String> calculatedTriggered = new ArrayList<>();
        if (reflexAnalyses != null) {
            for (Analysis reflex : reflexAnalyses) {
                try {
                    String acc = analysisService.getOrderAccessionNumber(reflex);
                    if (reflex.getResultCalculated()) {
                        calculatedTriggered.add(acc);
                    } else {
                        reflexTriggered.add(acc);
                    }
                } catch (Exception e) {
                    LOG.warn("{} could not get accession for reflex analysis: {}", LOG_PREFIX, e.getMessage());
                }
            }
        }

        // ── 17. resolve test name for response ────────────────────────────────
        String testName = "";
        try {
            testName = analysisService.getTestDisplayName(analysis);
        } catch (Exception e) {
            LOG.warn("{} could not resolve testName for response: {}", LOG_PREFIX, e.getMessage());
        }

        LOG.info("{} result submitted: sampleNumber={}, analysisId={}, status={}", LOG_PREFIX,
                request.getSampleNumber(), request.getAnalysisId(), writtenStatus);

        return NidanResultResponse.success(request.getSampleNumber(), request.getAnalysisId(),
                sample.getAccessionNumber(), testName, request.getResultValue(), resultType, writtenStatus,
                reflexTriggered, calculatedTriggered);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /** Throws BAD_REQUEST if any required field is missing. */
    private void validateSubmitRequest(NidanResultRequest request, String sysUserId) throws NidanResultException {
        if (request == null) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST, "Request body is required");
        }
        if (GenericValidator.isBlankOrNull(request.getSampleNumber())) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST, "sampleNumber is required");
        }
        if (GenericValidator.isBlankOrNull(request.getAnalysisId())) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST, "analysisId is required");
        }
        if (GenericValidator.isBlankOrNull(request.getResultValue())) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST, "resultValue is required");
        }
        if (GenericValidator.isBlankOrNull(sysUserId)) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST,
                    "Could not resolve authenticated user — ensure credentials are correct");
        }
    }

    /** Looks up sample by sampleNumber; throws NOT_FOUND or INTERNAL_ERROR. */
    private Sample resolveSample(String sampleNumber) throws NidanResultException {
        Sample sample;
        try {
            sample = sampleService.getSampleBySampleNumber(sampleNumber.trim());
        } catch (Exception e) {
            LOG.error("{} error looking up sample for sampleNumber={}: {}", LOG_PREFIX, sampleNumber, e.getMessage(),
                    e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error looking up sample: " + e.getMessage(), e);
        }
        if (sample == null) {
            throw new NidanResultException(NidanResultException.Kind.NOT_FOUND,
                    "No sample found for sampleNumber: " + sampleNumber);
        }
        return sample;
    }

    /**
     * Loads the analysis and validates it belongs to the given sample and is in a
     * state that allows result entry.
     */
    private Analysis resolveAnalysis(String analysisId, Sample sample) throws NidanResultException {
        Analysis analysis;
        try {
            analysis = analysisService.getAnalysisById(analysisId);
        } catch (Exception e) {
            LOG.error("{} error loading analysis {}: {}", LOG_PREFIX, analysisId, e.getMessage(), e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error loading analysis: " + e.getMessage(), e);
        }
        if (analysis == null) {
            throw new NidanResultException(NidanResultException.Kind.NOT_FOUND, "Analysis not found: " + analysisId);
        }

        // verify the analysis belongs to the sample — prevents cross-sample writes
        try {
            String analysisSampleId = analysis.getSampleItem().getSample().getId();
            if (!sample.getId().equals(analysisSampleId)) {
                throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST,
                        "Analysis " + analysisId + " does not belong to sampleNumber " + sample.getSampleNumber());
            }
        } catch (NidanResultException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("{} error verifying analysis ownership: {}", LOG_PREFIX, e.getMessage(), e);
            throw new NidanResultException(NidanResultException.Kind.INTERNAL_ERROR,
                    "Error verifying analysis ownership: " + e.getMessage(), e);
        }

        // guard: do not allow posting results against a canceled analysis
        String canceledId = statusService.getStatusID(AnalysisStatus.Canceled);
        if (canceledId.equals(analysis.getStatusId())) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST,
                    "Cannot post result: analysis " + analysisId + " is canceled");
        }

        // guard: do not allow overwriting a finalized result
        String finalizedId = statusService.getStatusID(AnalysisStatus.Finalized);
        if (finalizedId.equals(analysis.getStatusId())) {
            throw new NidanResultException(NidanResultException.Kind.BAD_REQUEST,
                    "Cannot post result: analysis " + analysisId + " is already finalized. Use the UI to amend.");
        }

        return analysis;
    }

    /**
     * Fetches the result limit for this analysis using
     * {@link ResultLimitService#getResultLimitForAnalysis}. Returns a non-null
     * {@link ResultLimit} — falls back to an empty (but non-null) ResultLimit on
     * any error, which causes the status logic to default to TechnicalAcceptance
     * (the safe/conservative choice).
     */
    private ResultLimit resolveResultLimit(Analysis analysis) {
        try {
            ResultLimit rl = resultLimitService.getResultLimitForAnalysis(analysis);
            if (rl != null) {
                return rl;
            }
        } catch (Exception e) {
            LOG.warn("{} could not resolve resultLimit for analysisId={}: {}", LOG_PREFIX, analysis.getId(),
                    e.getMessage());
        }
        // safe fallback — empty ResultLimit (no id, no range constraints)
        return new ResultLimit();
    }

    /**
     * Normalises the result type: accepts N/D/R (case-insensitive). Defaults to N
     * if absent or unrecognised.
     */
    private String normaliseResultType(String raw) {
        if (GenericValidator.isBlankOrNull(raw)) {
            return "N";
        }
        String upper = raw.trim().toUpperCase();
        if ("N".equals(upper) || "D".equals(upper) || "R".equals(upper)) {
            return upper;
        }
        LOG.warn("{} unrecognised resultType '{}' — defaulting to N", LOG_PREFIX, raw);
        return "N";
    }

    /**
     * Returns the testDate to use. If the caller supplied one it is used as-is (the
     * UI also sends raw date strings without server-side format enforcement beyond
     * the validator annotations — here the validator is intentionally bypassed
     * since this is a machine-to-machine call and dates may be in different
     * locales). Defaults to today when absent.
     */
    private String resolveTestDate(String testDate) {
        if (!GenericValidator.isBlankOrNull(testDate)) {
            return testDate.trim();
        }
        return DateUtil.convertSqlDateToStringDate(DateUtil.getNowAsSqlDate());
    }

    /**
     * Builds a fully-populated {@link TestResultItem} that will pass through
     * {@code ResultUtil.createResultsFromItems} correctly.
     *
     * <p>
     * Field-by-field rationale:
     * <ul>
     * <li>{@code analysisId} — required by ResultUtil to load the Analysis.</li>
     * <li>{@code testId} — required by ResultSaveService for result type
     * matching.</li>
     * <li>{@code resultValue} / {@code shadowResultValue} — both must be set;
     * {@code setResultValue} syncs shadow automatically, but we set shadow again
     * explicitly for clarity.</li>
     * <li>{@code resultType} — drives ResultSaveService branch (N/D/R).</li>
     * <li>{@code isModified=true} — required for filterModifiedItems to include
     * it.</li>
     * <li>{@code accessionNumber} — required by addResult for sample lookup.</li>
     * <li>{@code testDate} — used for analysis.completedDate.</li>
     * <li>{@code resultLimitId} — drives getStatusForTestResult: Finalized vs
     * TechnicalAcceptance based on normal range and alwaysValidate flag.</li>
     * <li>{@code lowerNormalRange} / {@code upperNormalRange} — needed by
     * ResultSaveBean for range storage.</li>
     * <li>{@code reportable=false} — matches UI default for new results.</li>
     * <li>{@code shadowRejected=false}, {@code refer=false} — these must be
     * explicitly false or ResultUtil takes reject/referral branches.</li>
     * </ul>
     */
    private TestResultItem buildTestResultItem(NidanResultRequest request, Analysis analysis, Sample sample,
            ResultLimit resultLimit, String resultType, String testDate, String sysUserId) {

        TestResultItem item = new TestResultItem();

        // analysis identity
        item.setAnalysisId(analysis.getId());

        // check if a Result record already exists for this analysis (enables update in
        // DB & note enforcement)
        try {
            List<Result> existingResults = resultService.getResultsByAnalysis(analysis);
            if (existingResults != null && !existingResults.isEmpty()) {
                for (Result existing : existingResults) {
                    if (existing != null && existing.getParentResult() == null
                            && !GenericValidator.isBlankOrNull(existing.getId())) {
                        item.setResultId(existing.getId());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("{} could not lookup existing results for analysisId={}: {}", LOG_PREFIX, analysis.getId(),
                    e.getMessage());
        }

        // test identity — needed by ResultSaveService
        try {
            if (analysis.getTest() != null) {
                item.setTestId(analysis.getTest().getId());
                item.setTestName(analysisService.getTestDisplayName(analysis));
            }
        } catch (Exception e) {
            LOG.warn("{} could not set testId/testName on item: {}", LOG_PREFIX, e.getMessage());
        }

        // result value — setResultValue also syncs shadowResultValue
        item.setResultValue(request.getResultValue());
        item.setShadowResultValue(request.getResultValue()); // explicit for clarity

        // result type
        item.setResultType(resultType);

        // mark as modified — REQUIRED for filterModifiedItems to pick this up
        item.setIsModified(true);

        // accession number — used by ResultUtil.addResult for sample lookup
        item.setAccessionNumber(sample.getAccessionNumber());

        // test date
        item.setTestDate(testDate);

        // result limit — drives Finalized vs TechnicalAcceptance decision
        if (resultLimit.getId() != null) {
            item.setResultLimitId(resultLimit.getId());
        }

        // normal range values — stored on the Result row via ResultSaveBean
        item.setLowerNormalRange(resultLimit.getLowNormal());
        item.setUpperNormalRange(resultLimit.getHighNormal());

        // valid flag — null resultLimit means we don't know if result is valid;
        // set to false to force TechnicalAcceptance (the conservative path)
        boolean hasLimit = resultLimit.getId() != null;
        item.setValid(hasLimit);

        // note — if caller provided note use it; if updating an existing result without
        // note, supply default placeholder note
        if (!GenericValidator.isBlankOrNull(request.getNote())) {
            item.setNote(request.getNote().trim());
        } else if (!GenericValidator.isBlankOrNull(item.getResultId())) {
            item.setNote("[Result has been updated through external system. Please verify]");
        }

        // explicit false for safety — prevents ResultUtil from taking reject/referral
        // paths
        item.setShadowRejected(false);
        item.setRefer(false);
        item.setReferredOut(false);
        item.setShadowReferredOut(false);

        // reportable — UI defaults to N for new results
        item.setReportable(false);

        // no reflex selection from programmatic entry
        item.setReflexJSONResult(null);

        return item;
    }

    /**
     * Reads the current statusId from the analysis after persist and maps to a
     * name.
     */
    private String resolveWrittenStatus(String analysisId) {
        try {
            Analysis fresh = analysisService.getAnalysisById(analysisId);
            if (fresh == null) {
                return "Unknown";
            }
            String sid = fresh.getStatusId();
            String techId = statusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
            String finId = statusService.getStatusID(AnalysisStatus.Finalized);
            if (techId.equals(sid))
                return "TechnicalAcceptance";
            if (finId.equals(sid))
                return "Finalized";
            return sid;
        } catch (Exception e) {
            LOG.warn("{} could not read written status for analysisId={}: {}", LOG_PREFIX, analysisId, e.getMessage());
            return "Unknown";
        }
    }

    /** Maps a numeric statusId to a human-readable string for the pending list. */
    private String statusIdToName(String statusId, String notStartedId, String techAcceptanceId) {
        if (notStartedId.equals(statusId))
            return "NotStarted";
        if (techAcceptanceId.equals(statusId))
            return "TechnicalAcceptance";
        return statusId;
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s.trim();
    }
}
