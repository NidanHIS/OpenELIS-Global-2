package org.openelisglobal.testcalculated.action.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.jfree.util.Log;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.note.service.NoteService;
import org.openelisglobal.note.service.NoteServiceImpl.NoteType;
import org.openelisglobal.note.valueholder.Note;
import org.openelisglobal.result.action.util.ResultSet;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.resultlimit.service.ResultLimitService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testcalculated.service.ResultCalculationService;
import org.openelisglobal.testcalculated.service.TestCalculationService;
import org.openelisglobal.testcalculated.valueholder.Calculation;
import org.openelisglobal.testcalculated.valueholder.Operation;
import org.openelisglobal.testcalculated.valueholder.ResultCalculation;
import org.openelisglobal.testresult.service.TestResultService;
import org.openelisglobal.testresult.valueholder.TestResult;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

@Service
@DependsOn({ "springContext" })
public class TestCalculatedUtil {

    private TestResultService testResultService = SpringContext.getBean(TestResultService.class);

    private ResultCalculationService resultcalculationService = SpringContext.getBean(ResultCalculationService.class);

    private TestCalculationService calculationService = SpringContext.getBean(TestCalculationService.class);

    private TestService testService = SpringContext.getBean(TestService.class);

    private ResultService resultService = SpringContext.getBean(ResultService.class);

    private AnalysisService analysisService = SpringContext.getBean(AnalysisService.class);

    private NoteService noteService = SpringContext.getBean(NoteService.class);

    private ResultLimitService resultLimitService = SpringContext.getBean(ResultLimitService.class);

    private String CALCULATION_SUBJECT = "Calculated Result Note";

    private boolean isCalculationForSample(ResultCalculation rc, Sample currentSample) {
        if (rc == null || currentSample == null) {
            return false;
        }
        if (rc.getResult() != null && rc.getResult().getAnalysis() != null
                && rc.getResult().getAnalysis().getSampleItem() != null) {
            Sample s = rc.getResult().getAnalysis().getSampleItem().getSample();
            if (s != null && s.getId() != null) {
                return s.getId().equals(currentSample.getId());
            }
        }
        if (rc.getTestResultMap() != null && !rc.getTestResultMap().isEmpty()) {
            for (Integer resId : rc.getTestResultMap().values()) {
                if (resId != null) {
                    Result r = resultService.get(resId.toString());
                    if (r != null && r.getAnalysis() != null && r.getAnalysis().getSampleItem() != null) {
                        Sample s = r.getAnalysis().getSampleItem().getSample();
                        if (s != null && s.getId() != null) {
                            return s.getId().equals(currentSample.getId());
                        }
                    }
                }
            }
        }
        return false;
    }

    public List<Analysis> addNewTestsToDBForCalculatedTests(List<ResultSet> resultSetList, String sysUserId)
            throws IllegalStateException {
        List<Analysis> analyses = new ArrayList<>();
        for (ResultSet resultSet : resultSetList) {
            if (resultSet.result == null || resultSet.result.getTestResult() == null) {
                continue;
            }
            Sample currentSample = (resultSet.result.getAnalysis() != null
                    && resultSet.result.getAnalysis().getSampleItem() != null)
                            ? resultSet.result.getAnalysis().getSampleItem().getSample()
                            : null;
            if (currentSample == null) {
                continue;
            }

            List<Calculation> calculations = calculationService.getAll();
            for (Calculation calculation : calculations) {
                if (!calculation.getActive()) {
                    continue;
                }
                List<ResultCalculation> patientCalculations = resultcalculationService
                        .getResultCalculationByPatientAndCalculation(resultSet.patient, calculation);
                List<ResultCalculation> sampleCalculations = new ArrayList<>();
                for (ResultCalculation rc : patientCalculations) {
                    if (isCalculationForSample(rc, currentSample)) {
                        sampleCalculations.add(rc);
                    }
                }

                if (sampleCalculations.isEmpty()) {
                    Boolean createResultCalculation = false;
                    for (Operation oper : calculation.getOperations()) {
                        if (oper.getType().equals(Operation.OperationType.TEST_RESULT)) {
                            if (Integer.valueOf(oper.getValue())
                                    .equals(Integer.valueOf(resultSet.result.getTestResult().getTest().getId()))) {
                                createResultCalculation = true;
                                break;
                            }
                        }
                    }
                    if (createResultCalculation) {
                        ResultCalculation calc = new ResultCalculation();
                        calc.setCalculation(calculation);
                        calc.setPatient(resultSet.patient);
                        Set<Test> tests = new HashSet<>();
                        calculation.getOperations().forEach(oper -> {
                            if (oper.getType().equals(Operation.OperationType.TEST_RESULT)) {
                                Test test = testService.getActiveTestById(Integer.valueOf(oper.getValue()));
                                tests.add(test);
                            }
                        });
                        calc.setTest(tests);
                        Map<Integer, Integer> map = new HashMap<>();
                        tests.forEach(test -> {
                            map.put(Integer.valueOf(test.getId()), null);
                        });
                        // Insert initial result value only if it is an input test for this calculation
                        if (resultSet.result.getTestResult().getTest().getId() != null
                                && resultSet.result.getId() != null) {
                            Integer currentTestId = Integer.valueOf(resultSet.result.getTestResult().getTest().getId());
                            if (map.containsKey(currentTestId)) {
                                map.put(currentTestId, Integer.valueOf(resultSet.result.getId()));
                            }
                        }
                        calc.setTestResultMap(map);
                        resultcalculationService.insert(calc);
                    }

                } else {
                    for (ResultCalculation resultCalculation : sampleCalculations) {
                        if (resultSet.result.getTestResult().getTest().getId() != null
                                && resultSet.result.getId() != null) {
                            Integer currentTestId = Integer.valueOf(resultSet.result.getTestResult().getTest().getId());
                            // Only map results for tests that are part of this calculation's input formula
                            if (resultCalculation.getTestResultMap().containsKey(currentTestId)) {
                                resultCalculation.getTestResultMap().put(currentTestId,
                                        Integer.valueOf(resultSet.result.getId()));
                                resultcalculationService.update(resultCalculation);
                            }
                        }
                    }
                }
            }
        }

        for (ResultSet resultSet : resultSetList) {
            if (resultSet.result == null || resultSet.result.getTestResult() == null) {
                continue;
            }
            Sample currentSample = (resultSet.result.getAnalysis() != null
                    && resultSet.result.getAnalysis().getSampleItem() != null)
                            ? resultSet.result.getAnalysis().getSampleItem().getSample()
                            : null;
            if (currentSample == null) {
                continue;
            }

            List<ResultCalculation> patientCalculations = resultcalculationService.getResultCalculationByPatientAndTest(
                    resultSet.patient, resultSet.result.getTestResult().getTest());
            List<ResultCalculation> sampleCalculations = new ArrayList<>();
            for (ResultCalculation rc : patientCalculations) {
                if (isCalculationForSample(rc, currentSample)) {
                    sampleCalculations.add(rc);
                }
            }

            if (!sampleCalculations.isEmpty()) {
                for (ResultCalculation resultCalculation : sampleCalculations) {
                    Boolean isMissingParams = false;
                    for (Map.Entry<Integer, Integer> entry : resultCalculation.getTestResultMap().entrySet()) {
                        if (entry.getValue() == null) {
                            isMissingParams = true;
                            break;
                        }
                    }
                    Calculation calculation = resultCalculation.getCalculation();
                    if (!isMissingParams) {
                        StringBuffer function = new StringBuffer();
                        calculation.getOperations().forEach(operation -> {
                            switch (operation.getType()) {
                            case TEST_RESULT:
                                addNumericOperation(operation, resultCalculation, function,
                                        Operation.OperationType.TEST_RESULT.toString());
                                break;
                            case INTEGER:
                                try {
                                    if (operation.getValue().contains(".")) {
                                        double val = Double.parseDouble(operation.getValue());
                                        function.append(val).append(" ");
                                    } else {
                                        int number = Integer.parseInt(operation.getValue());
                                        function.append(number).append(" ");
                                    }
                                } catch (NumberFormatException e) {
                                    LogEvent.logWarn("TestCalculatedUtil", "buildFunction",
                                            "Bad INTEGER operand in calc '" + calculation.getName() + "': ["
                                                    + operation.getValue() + "]");
                                }
                                break;
                            case MATH_FUNCTION:
                                if (operation.getValue().equals(Operation.IN_NORMAL_RANGE)) {
                                    int order = operation.getOrder();
                                    List<Operation> ops = calculation.getOperations();
                                    if (order > 0 && (order - 1) < ops.size()) {
                                        addNumericOperation(ops.get(order - 1), resultCalculation, function,
                                                Operation.IN_NORMAL_RANGE);
                                    } else {
                                        LogEvent.logWarn("TestCalculatedUtil", "buildFunction",
                                                "IN_NORMAL_RANGE has invalid order=" + order + " in calc '"
                                                        + calculation.getName() + "'");
                                    }
                                } else if (operation.getValue().equals(Operation.OUTSIDE_NORMAL_RANGE)) {
                                    int order = operation.getOrder();
                                    List<Operation> ops = calculation.getOperations();
                                    if (order > 0 && (order - 1) < ops.size()) {
                                        addNumericOperation(ops.get(order - 1), resultCalculation, function,
                                                Operation.OUTSIDE_NORMAL_RANGE);
                                    } else {
                                        LogEvent.logWarn("TestCalculatedUtil", "buildFunction",
                                                "OUTSIDE_NORMAL_RANGE has invalid order=" + order + " in calc '"
                                                        + calculation.getName() + "'");
                                    }
                                } else {
                                    function.append(operation.getValue()).append(" ");
                                }
                                break;
                            case PATIENT_ATTRIBUTE:
                                if (operation.getValue().equals(Operation.PatientAttribute.AGE.toString())) {
                                    if (resultSet.patient != null && resultSet.patient.getBirthDate() != null) {
                                        int age = DateUtil.getAgeInYears(
                                                new Date(resultSet.patient.getBirthDate().getTime()), new Date());
                                        function.append(age);
                                    } else {
                                        LogEvent.logWarn("TestCalculatedUtil", "buildFunction",
                                                "Patient birthdate missing – age operand skipped in calc '"
                                                        + calculation.getName() + "'");
                                    }
                                }
                                break;
                            }
                        });
                        ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
                        ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("JavaScript");
                        String value = null;
                        try {
                            Log.debug("Caliculation Rule: " + calculation.getName() + " Function : "
                                    + function.toString());
                            value = scriptEngine.eval(function.toString()).toString();
                            Log.debug("Caliculation Rule: " + calculation.getName() + " Value  : " + value);
                        } catch (ScriptException e) {
                            Log.error("Invalid Caliculation Rule: " + calculation.getName(), e);
                        }
                        Analysis analysis = createCalculatedResult(resultCalculation, resultSet, calculation, value,
                                sysUserId);
                        if (analysis != null) {
                            analyses.add(analysis);
                        }

                    } else {
                        Analysis analysis = createCalculatedResult(resultCalculation, resultSet, calculation, null,
                                sysUserId);
                        if (analysis != null) {
                            analyses.add(analysis);
                        }
                    }
                }
            }
        }
        return analyses;
    }

    private Analysis createCalculatedResult(ResultCalculation resultCalculation, ResultSet resultSet,
            Calculation calculation, String value, String systemUserId) {
        Test test = testService.get(calculation.getTestId().toString());
        if (test == null) {
            return null;
        }

        // NUMERIC-ONLY GATE: Auto-calculations are only supported for numeric (N)
        // result types.
        String resultType = testService.getResultType(test);
        if (!"N".equals(resultType)) {
            LogEvent.logWarn("TestCalculatedUtil", "createCalculatedResult",
                    "Auto-calc skipped: test " + test.getId() + " is not numeric (type=" + resultType + ")");
            return null;
        }

        SampleItem currentSampleItem = (resultSet.result != null && resultSet.result.getAnalysis() != null)
                ? resultSet.result.getAnalysis().getSampleItem()
                : null;
        Sample currentSample = (currentSampleItem != null) ? currentSampleItem.getSample() : null;
        if (currentSample == null || currentSample.getId() == null) {
            return null;
        }

        // HARD SAFEGUARD 1: Check across the ENTIRE sample order (all tubes)
        // Auto-calculation ONLY executes if the target test was explicitly ordered!
        Analysis targetOrderedAnalysis = null;
        List<Analysis> analysesOnSample = analysisService.getAnalysesBySampleId(currentSample.getId());
        if (analysesOnSample != null) {
            for (Analysis a : analysesOnSample) {
                if (a.getTest() != null && a.getTest().getId().equals(test.getId())
                        && !Boolean.TRUE.equals(a.getResultCalculated())) {
                    targetOrderedAnalysis = a;
                    break;
                }
            }
        }

        if (targetOrderedAnalysis == null) {
            // Target test was NOT ordered on this sample:
            // HARD SAFEGUARD: DO NOT create analysis. DO NOT touch Result. Return null
            // immediately!
            return null;
        }

        String validSysUserId = !GenericValidator.isBlankOrNull(systemUserId) ? systemUserId : "1";

        // Check if this calculation rule triggers external note only
        if (Boolean.valueOf(value)) {
            if (StringUtils.isNotBlank(calculation.getNote())) {
                Note note = noteService.createSavableNote(targetOrderedAnalysis, NoteType.EXTERNAL,
                        calculation.getNote(), CALCULATION_SUBJECT, validSysUserId);
                if (!noteService.duplicateNoteExists(note)) {
                    noteService.save(note);
                }
            }
            return targetOrderedAnalysis;
        }

        TestResult testResult = getTestResultForCalculation(calculation);
        Boolean resultCalculated = false;

        // Numeric type only — round to significant digits if configured
        if (value != null) {
            if (testResult != null && testResult.getSignificantDigits() != null) {
                try {
                    double factor = Math.pow(10, Double.valueOf(testResult.getSignificantDigits()));
                    value = String.valueOf(Math.round(Double.valueOf(value) * factor) / factor);
                } catch (NumberFormatException e) {
                    // keep unformatted value
                }
            }
            resultCalculated = true;
        }

        // HARD SAFEGUARD 2: Zero background Result mutation.
        // Idempotently park the calculated value on the ordered Analysis.
        // Never insert or update clinlims.result!
        // Never alter analysis.status_id!
        if (resultCalculated && !GenericValidator.isBlankOrNull(value)) {
            // Defensive truncation — keep within DB column constraint (VARCHAR 255)
            String safeValue = value.length() > 250 ? value.substring(0, 250) : value;
            targetOrderedAnalysis.setPendingCalculatedValue(safeValue);
            targetOrderedAnalysis.setPendingCalculationName(calculation.getName());
        } else {
            targetOrderedAnalysis.setPendingCalculatedValue(null);
            targetOrderedAnalysis.setPendingCalculationName(null);
        }
        targetOrderedAnalysis.setSysUserId(validSysUserId);
        analysisService.update(targetOrderedAnalysis);

        List<Result> existingResults = resultService.getResultsByAnalysis(targetOrderedAnalysis);
        if (existingResults != null && !existingResults.isEmpty()) {
            resultCalculation.setResult(existingResults.get(0));
            resultcalculationService.update(resultCalculation);
        }

        return targetOrderedAnalysis;
    }

    private void createInternalNote(Analysis newAnalysis, Analysis currentAnalysis, String calculatioName,
            String systemUserId, String externalNote) {
        List<Note> notes = new ArrayList<>();
        Note note = noteService.createSavableNote(newAnalysis, NoteType.INTERNAL,
                "Result Succesfully Calculated From Calculation Rule :" + calculatioName, CALCULATION_SUBJECT,
                systemUserId);
        if (!noteService.duplicateNoteExists(note)) {
            notes.add(note);
        }

        Note note2 = noteService.createSavableNote(newAnalysis, NoteType.INTERNAL,
                "Calculation Parameters include Result of Test "
                        + currentAnalysis.getTest().getLocalizedReportingName().getLocalizedValue(),
                CALCULATION_SUBJECT, systemUserId);
        if (!noteService.duplicateNoteExists(note2)) {
            notes.add(note2);
        }

        if (StringUtils.isNotBlank(externalNote)) {
            Note note3 = noteService.createSavableNote(newAnalysis, NoteType.EXTERNAL, externalNote,
                    CALCULATION_SUBJECT, systemUserId);
            if (!noteService.duplicateNoteExists(note3)) {
                notes.add(note3);
            }
        }

        noteService.saveAll(notes);
    }

    private void createMissingValueInternalNote(Analysis newAnalysis, Analysis currentAnalysis, String calculatioName,
            String systemUserId) {
        List<Note> notes = new ArrayList<>();
        Note note = noteService.createSavableNote(newAnalysis, NoteType.INTERNAL,
                "Result Missing Calculation Parameters From Calculation Rule : " + calculatioName, CALCULATION_SUBJECT,
                systemUserId);
        if (!noteService.duplicateNoteExists(note)) {
            notes.add(note);
        }
        Note note2 = noteService.createSavableNote(newAnalysis, NoteType.INTERNAL,
                "Calculation Parameters include Result of Test : "
                        + currentAnalysis.getTest().getLocalizedReportingName().getLocalizedValue(),
                CALCULATION_SUBJECT, systemUserId);
        if (!noteService.duplicateNoteExists(note2)) {
            notes.add(note2);
        }
        noteService.saveAll(notes);
    }

    private TestResult getTestResultForCalculation(Calculation calculation) {
        Test test = testService.get(calculation.getTestId().toString());
        String resultType = testService.getResultType(test);
        if ("D".equals(resultType)) {
            TestResult testResult;
            testResult = testResultService.getTestResultsByTestAndDictonaryResult(test.getId(),
                    calculation.getResult());
            return testResult;
        } else {
            List<TestResult> testResultList = testResultService.getActiveTestResultsByTest(test.getId());
            // we are assuming there is only one testResult for a numeric
            // type result
            if (!testResultList.isEmpty()) {
                // get the latest modified test result
                return testResultList.get(testResultList.size() - 1);
            }
        }

        return null;
    }

    private void addNumericOperation(Operation operation, ResultCalculation resultCalculation, StringBuffer function,
            String inputType) {
        Test test = testService.getActiveTestById(Integer.valueOf(operation.getValue()));
        if (test != null) {
            Integer resultId = resultCalculation.getTestResultMap().get(Integer.valueOf(test.getId()));
            Result result = null;
            if (resultId != null) {
                result = resultService.get(resultId.toString());
            }

            if (result != null) {
                if (testService.getResultType(result.getTestResult().getTest()).equals("N")) {
                    switch (inputType) {
                    case Operation.TEST_RESULT:
                        function.append(result.getValue()).append(" ");
                        break;
                    case Operation.IN_NORMAL_RANGE:
                        function.append(" >= ")
                                .append(result.getMinNormal() != null ? result.getMinNormal()
                                        : Double.NEGATIVE_INFINITY)
                                .append(" && ").append(result.getValue()).append(" <= ")
                                .append(result.getMaxNormal() != null ? result.getMaxNormal()
                                        : Double.POSITIVE_INFINITY)
                                .append(" ");
                        break;
                    case Operation.OUTSIDE_NORMAL_RANGE:
                        function.append(" <= ")
                                .append(result.getMinNormal() != null ? result.getMinNormal()
                                        : Double.NEGATIVE_INFINITY)
                                .append(" || ").append(result.getValue()).append(" >= ")
                                .append(result.getMaxNormal() != null ? result.getMaxNormal()
                                        : Double.POSITIVE_INFINITY)
                                .append(" ");
                        break;
                    }
                }
            }
        }
    }

    private Analysis createCalculatedAnalysis(Analysis existingAnalysis, Test test, Result result, String value,
            String calculationName, String systemUserId, Boolean resultCalculated, String externalNote) {
        Analysis currentAnalysis = result.getAnalysis();
        Analysis generatedAnalysis = null;
        if (existingAnalysis != null) {
            generatedAnalysis = analysisService.get(existingAnalysis.getId());
        } else {
            generatedAnalysis = new Analysis();
        }
        generatedAnalysis.setTest(test);
        generatedAnalysis.setIsReportable(currentAnalysis.getIsReportable());
        generatedAnalysis.setAnalysisType(currentAnalysis.getAnalysisType());
        generatedAnalysis.setRevision(currentAnalysis.getRevision());
        generatedAnalysis.setStartedDate(DateUtil.getNowAsSqlDate());
        if (resultCalculated) {
            generatedAnalysis.setStatusId(
                    SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.TechnicalAcceptance));
        } else {
            generatedAnalysis
                    .setStatusId(SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.NotStarted));
        }
        generatedAnalysis.setParentAnalysis(currentAnalysis);
        generatedAnalysis.setParentResult(result);
        // When reusing an existing manually-ordered analysis (existingAnalysis !=
        // null),
        // preserve its own testSection and sampleItem — do NOT overwrite them with the
        // triggering input test's section/sample, which would move D into A's
        // department.
        if (existingAnalysis == null) {
            generatedAnalysis.setSampleItem(currentAnalysis.getSampleItem());
            generatedAnalysis.setTestSection(currentAnalysis.getTestSection());
            generatedAnalysis.setSampleTypeName(currentAnalysis.getSampleTypeName());
        }
        generatedAnalysis.setSysUserId(systemUserId);
        generatedAnalysis.setResultCalculated(resultCalculated);
        if (existingAnalysis != null) {
            try {
                analysisService.update(generatedAnalysis);
            } catch (Exception e) {
                return null;
            }

        } else {
            try {
                analysisService.insert(generatedAnalysis);
            } catch (Exception e) {
                return null;
            }
        }
        if (resultCalculated) {
            createInternalNote(generatedAnalysis, currentAnalysis, calculationName, systemUserId, externalNote);
        } else {
            createMissingValueInternalNote(generatedAnalysis, currentAnalysis, calculationName, systemUserId);
        }
        return generatedAnalysis;
    }
}
