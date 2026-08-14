package org.openelisglobal.sample.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openelisglobal.sample.valueholder.Sample;

public class SampleNumberServiceTest {

    @Test
    public void testSampleEntity_SampleNumberGetterSetter() {
        Sample sample = new Sample();
        sample.setSampleNumber("1408-0001");
        assertEquals("1408-0001", sample.getSampleNumber());
    }

    @Test
    public void testSampleEntity_AccessionNumberPreserved() {
        Sample sample = new Sample();
        sample.setAccessionNumber("DEV01260000000000005");
        assertEquals("DEV01260000000000005", sample.getAccessionNumber());
    }

    @Test
    public void testSampleEntity_BothNumbersIndependent() {
        Sample sample = new Sample();
        sample.setAccessionNumber("DEV01260000000000005");
        sample.setSampleNumber("1408-0001");
        assertEquals("DEV01260000000000005", sample.getAccessionNumber());
        assertEquals("1408-0001", sample.getSampleNumber());
    }
}
