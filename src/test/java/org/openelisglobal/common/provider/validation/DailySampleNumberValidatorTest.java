package org.openelisglobal.common.provider.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator.ValidationResults;

public class DailySampleNumberValidatorTest {

    private DailySampleNumberValidator validator;

    @Before
    public void setUp() {
        validator = new DailySampleNumberValidator();
    }

    @Test
    public void testValidFormat_Success() {
        assertEquals(ValidationResults.SUCCESS, validator.validFormat("1408-0001", true));
        assertEquals(ValidationResults.SUCCESS, validator.validFormat("0101-9999", true));
        assertEquals(ValidationResults.SUCCESS, validator.validFormat("1408-0001-1", true));
    }

    @Test
    public void testValidFormat_Failure() {
        assertEquals(ValidationResults.FORMAT_FAIL, validator.validFormat(null, true));
        assertEquals(ValidationResults.FORMAT_FAIL, validator.validFormat("", true));
        assertEquals(ValidationResults.FORMAT_FAIL, validator.validFormat("ABC-1234", true));
        assertEquals(ValidationResults.FORMAT_FAIL, validator.validFormat("14080001", true));
        assertEquals(ValidationResults.FORMAT_FAIL, validator.validFormat("1408-0001;SELECT", true));
    }

    @Test
    public void testMetadataLengths() {
        assertEquals(9, validator.getMaxAccessionLength());
        assertEquals(9, validator.getMinAccessionLength());
        assertEquals(5, validator.getInvarientLength());
        assertEquals(4, validator.getChangeableLength());
        assertNotNull(validator.getPrefix());
        assertTrue(validator.getPrefix().endsWith("-"));
        assertFalse(validator.needProgramCode());
    }
}
