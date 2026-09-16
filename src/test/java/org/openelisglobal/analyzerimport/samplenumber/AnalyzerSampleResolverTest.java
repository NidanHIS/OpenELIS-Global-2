package org.openelisglobal.analyzerimport.samplenumber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.util.SampleNumberUtil;
import org.openelisglobal.sample.valueholder.Sample;

@RunWith(MockitoJUnitRunner.class)
public class AnalyzerSampleResolverTest {

    @Mock
    private SampleService sampleService;
    @Mock
    private SampleNumberCandidateFilter candidateFilter;

    @InjectMocks
    private AnalyzerSampleResolver resolver;

    @Test
    public void blank_isNone() {
        assertTrue(resolver.resolve("  ", "7").isNone());
        assertTrue(resolver.resolve(null, "7").isNone());
    }

    @Test
    public void uniqueLookback_isUnique() {
        Sample today = sample("1", SampleNumberUtil.toStorage("1111"));
        when(sampleService.getSampleBySampleNumberExact("1111")).thenReturn(null);
        when(sampleService.findAutoSamplesByStoredNumbers(anyList())).thenReturn(Collections.singletonList(today));
        when(candidateFilter.isEligible(today, "7")).thenReturn(true);
        AnalyzerSampleResolveOutcome out = resolver.resolve("1111", "7");
        assertTrue(out.isUnique());
        assertEquals("1", out.getSample().getId());
    }

    @Test
    public void twoEligible_isAmbiguousNeverPicks() {
        Sample a = sample("1", "2609161111");
        Sample b = sample("2", "2609151111");
        when(sampleService.getSampleBySampleNumberExact("1111")).thenReturn(null);
        when(sampleService.findAutoSamplesByStoredNumbers(anyList())).thenReturn(Arrays.asList(a, b));
        when(candidateFilter.isEligible(a, "7")).thenReturn(true);
        when(candidateFilter.isEligible(b, "7")).thenReturn(true);
        AnalyzerSampleResolveOutcome out = resolver.resolve("1111", "7");
        assertTrue(out.isAmbiguous());
        assertEquals(2, out.getCandidates().size());
        assertTrue(out.getSample() == null);
    }

    @Test
    public void oneEligibleAfterFilter_isUnique() {
        Sample a = sample("1", "2609161111");
        Sample b = sample("2", "2609151111");
        when(sampleService.getSampleBySampleNumberExact("1111")).thenReturn(null);
        when(sampleService.findAutoSamplesByStoredNumbers(anyList())).thenReturn(Arrays.asList(a, b));
        when(candidateFilter.isEligible(a, "7")).thenReturn(true);
        when(candidateFilter.isEligible(b, "7")).thenReturn(false);
        AnalyzerSampleResolveOutcome out = resolver.resolve("1111", "7");
        assertTrue(out.isUnique());
        assertEquals("1", out.getSample().getId());
    }

    @Test
    public void emptyThenAccessionFallback() {
        Sample acc = sample("9", null);
        acc.setAccessionNumber("LAB-1");
        when(sampleService.getSampleBySampleNumberExact("LAB-1")).thenReturn(null);
        when(sampleService.getSampleByAccessionNumber("LAB-1")).thenReturn(acc);
        when(candidateFilter.isEligible(acc, "7")).thenReturn(true);
        AnalyzerSampleResolveOutcome out = resolver.resolve("LAB-1", "7");
        assertTrue(out.isUnique());
        assertEquals("9", out.getSample().getId());
    }

    @Test
    public void serviceThrow_isNone() {
        when(sampleService.getSampleBySampleNumberExact(anyString())).thenThrow(new RuntimeException("boom"));
        assertTrue(resolver.resolve("1111", "7").isNone());
    }

    private Sample sample(String id, String sampleNumber) {
        Sample sample = new Sample();
        sample.setId(id);
        sample.setSampleNumber(sampleNumber);
        sample.setAccessionNumber("ACC-" + id);
        return sample;
    }
}
