package org.openelisglobal.sample.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.junit.Test;

public class SampleNumberUtilTest {

    @Test
    public void toDisplay_stripsYyMmDdFromStoredAuto() {
        assertEquals("0001", SampleNumberUtil.toDisplay("2609160001"));
        assertEquals("1111", SampleNumberUtil.toDisplay("2601011111"));
    }

    @Test
    public void toDisplay_passesThroughNonStored() {
        assertEquals("0001", SampleNumberUtil.toDisplay("0001"));
        assertEquals("LAB01", SampleNumberUtil.toDisplay("LAB01"));
        assertNull(SampleNumberUtil.toDisplay(null));
        assertEquals("", SampleNumberUtil.toDisplay(""));
    }

    @Test
    public void toStorage_prependsClockDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0);
        Date asOf = cal.getTime();
        assertEquals("2609160001", SampleNumberUtil.toStorage("0001", asOf));
        assertEquals("2609160001", SampleNumberUtil.toStorage("2609160001", asOf));
        assertEquals("LAB01", SampleNumberUtil.toStorage("LAB01", asOf));
        assertNull(SampleNumberUtil.toStorage(null, asOf));
    }

    @Test
    public void isDisplayFormat_isExactlyFourDigits() {
        assertTrue(SampleNumberUtil.isDisplayFormat("0001"));
        assertTrue(SampleNumberUtil.isDisplayFormat("1111"));
        assertFalse(SampleNumberUtil.isDisplayFormat("060001"));
        assertFalse(SampleNumberUtil.isDisplayFormat("2609160001"));
        assertFalse(SampleNumberUtil.isDisplayFormat("LAB01"));
        assertFalse(SampleNumberUtil.isDisplayFormat(null));
    }

    @Test
    public void isStoredAutoFormat_isExactlyTenDigits() {
        assertTrue(SampleNumberUtil.isStoredAutoFormat("2609160001"));
        assertFalse(SampleNumberUtil.isStoredAutoFormat("0001"));
        assertFalse(SampleNumberUtil.isStoredAutoFormat("060001"));
    }

    @Test
    public void storageKeysForLookback_todayThenTwoPriorDays() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.SEPTEMBER, 16, 8, 0, 0);
        List<String> keys = SampleNumberUtil.storageKeysForLookback("1111", 3, cal.getTime());
        assertEquals(3, keys.size());
        assertEquals("2609161111", keys.get(0));
        assertEquals("2609151111", keys.get(1));
        assertEquals("2609141111", keys.get(2));
    }

    @Test
    public void storageKeysForLookback_rejectsNonFourDigit() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.SEPTEMBER, 16);
        assertTrue(SampleNumberUtil.storageKeysForLookback("060001", 3, cal.getTime()).isEmpty());
        assertTrue(SampleNumberUtil.storageKeysForLookback(null, 3, cal.getTime()).isEmpty());
    }
}
