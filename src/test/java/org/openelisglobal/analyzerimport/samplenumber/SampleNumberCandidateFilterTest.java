package org.openelisglobal.analyzerimport.samplenumber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.valueholder.Sample;

@RunWith(MockitoJUnitRunner.class)
public class SampleNumberCandidateFilterTest {

    @Mock
    private AnalysisService analysisService;
    @Mock
    private ResultService resultService;
    @Mock
    private IStatusService statusService;

    @InjectMocks
    private SampleNumberCandidateFilter filter;

    private Sample sample;

    @Before
    public void setUp() {
        sample = new Sample();
        sample.setId("10");
        when(statusService.getStatusID(AnalysisStatus.Canceled)).thenReturn("C");
        when(statusService.getStatusID(AnalysisStatus.TechnicalAcceptance)).thenReturn("TA");
        when(statusService.getStatusID(AnalysisStatus.Finalized)).thenReturn("F");
    }

    @Test
    public void notStartedNoResult_isEligible() {
        Analysis analysis = analysis("7", "NS");
        when(analysisService.getAnalysesBySampleId("10")).thenReturn(list(analysis));
        when(resultService.getResultsByAnalysis(analysis)).thenReturn(Collections.emptyList());
        assertTrue(filter.isEligible(sample, "7"));
    }

    @Test
    public void technicalAcceptance_notEligible() {
        Analysis analysis = analysis("7", "TA");
        when(analysisService.getAnalysesBySampleId("10")).thenReturn(list(analysis));
        assertFalse(filter.isEligible(sample, "7"));
    }

    @Test
    public void nonBlankResult_notEligible() {
        Analysis analysis = analysis("7", "NS");
        Result result = new Result();
        result.setValue("1.2");
        when(analysisService.getAnalysesBySampleId("10")).thenReturn(list(analysis));
        when(resultService.getResultsByAnalysis(analysis)).thenReturn(list(result));
        assertFalse(filter.isEligible(sample, "7"));
    }

    @Test
    public void testNotOrdered_notEligible() {
        Analysis analysis = analysis("9", "NS");
        when(analysisService.getAnalysesBySampleId("10")).thenReturn(list(analysis));
        assertFalse(filter.isEligible(sample, "7"));
    }

    @Test
    public void nullSample_notEligible() {
        assertFalse(filter.isEligible(null, "7"));
    }

    private Analysis analysis(String testId, String statusId) {
        org.openelisglobal.test.valueholder.Test test = new org.openelisglobal.test.valueholder.Test();
        test.setId(testId);
        Analysis analysis = new Analysis();
        analysis.setTest(test);
        analysis.setStatusId(statusId);
        return analysis;
    }

    @SafeVarargs
    private final <T> List<T> list(T... items) {
        List<T> out = new ArrayList<>();
        Collections.addAll(out, items);
        return out;
    }
}
