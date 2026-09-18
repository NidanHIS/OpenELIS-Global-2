package org.openelisglobal.common.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringUtilTest {

    private static final String VALID_INTEGER = "123";

    @Test
    public void isNullorNill_shouldReturnTrueForNull() {
        assertTrue(StringUtil.isNullorNill(null));
    }

    @Test
    public void isNullorNill_shouldReturnTrueForEmptyString() {
        assertTrue(StringUtil.isNullorNill(""));
    }

    @Test
    public void isNullorNill_shouldReturnTrueForNullString() {
        assertTrue(StringUtil.isNullorNill("null"));
    }

    @Test
    public void isNullorNill_shouldReturnFalseForValidString() {
        assertFalse(StringUtil.isNullorNill("test"));
    }

    @Test
    public void isInteger_shouldReturnTrueForValidInteger() {
        assertTrue(StringUtil.isInteger(VALID_INTEGER));
        assertTrue(StringUtil.isInteger("-456"));
    }

    @Test
    public void isInteger_shouldReturnFalseForInvalidValue() {
        assertFalse(StringUtil.isInteger("12.34"));
        assertFalse(StringUtil.isInteger("abc"));
    }

    @Test
    public void isNumeric_shouldReturnTrueForValidNumbers() {
        assertTrue(StringUtil.isNumeric(VALID_INTEGER));
        assertTrue(StringUtil.isNumeric("3.14"));
        assertTrue(StringUtil.isNumeric("-45.67"));
    }

    @Test
    public void isNumeric_shouldReturnFalseForInvalidValue() {
        assertFalse(StringUtil.isNumeric("abc"));
        assertFalse(StringUtil.isNumeric(null));
    }

    @Test
    public void blankIfNull_shouldReturnEmptyStringForNull() {
        assertEquals("", StringUtil.blankIfNull(null));
    }

    @Test
    public void blankIfNull_shouldReturnValueForNonNull() {
        assertEquals("test", StringUtil.blankIfNull("test"));
    }

    @Test
    public void safeEquals_shouldReturnTrueForBothNull() {
        assertTrue(StringUtil.safeEquals(null, null));
    }

    @Test
    public void safeEquals_shouldReturnTrueForEqualStrings() {
        assertTrue(StringUtil.safeEquals("hello", "hello"));
    }

    @Test
    public void safeEquals_shouldReturnFalseForDifferentStrings() {
        assertFalse(StringUtil.safeEquals("hello", "world"));
    }

    @Test
    public void containsOnly_shouldReturnTrueForMatchingChars() {
        assertTrue(StringUtil.containsOnly("aaaa", 'a'));
    }

    @Test
    public void containsOnly_shouldReturnFalseForMixedChars() {
        assertFalse(StringUtil.containsOnly("aaba", 'a'));
    }

    @Test
    public void containsOnly_shouldReturnFalseForNull() {
        assertFalse(StringUtil.containsOnly(null, 'a'));
    }

    @Test
    public void ellipsisString_shouldTruncateLongText() {
        assertEquals("Hello...", StringUtil.ellipsisString("Hello World", 5));
    }

    @Test
    public void ellipsisString_shouldReturnShortTextUnchanged() {
        assertEquals("Hi", StringUtil.ellipsisString("Hi", 10));
    }

    @Test
    public void capitalize_shouldCapitalizeFirstLetter() {
        assertEquals("Hello", StringUtil.capitalize("hello"));
    }

    @Test
    public void toArray_shouldSplitByComma() {
        String[] result = StringUtil.toArray("a, b, c");
        assertArrayEquals(new String[] { "a", "b", "c" }, result);
    }

    @Test
    public void toArray_shouldReturnEmptyArrayForNull() {
        assertArrayEquals(new String[0], StringUtil.toArray(null));
    }

    @Test
    public void replaceCharAtIndex_shouldReplaceCharacter() {
        assertEquals("hallo", StringUtil.replaceCharAtIndex("hello", 'a', 1));
    }

    @Test
    public void replaceCharAtIndex_shouldReturnUnchangedForInvalidIndex() {
        assertEquals("hello", StringUtil.replaceCharAtIndex("hello", 'a', -1));
    }

    @Test
    public void repeat_shouldRepeatString() {
        assertEquals("ababab", StringUtil.repeat("ab", 3));
    }

    @Test
    public void countInstances_shouldCountOccurrences() {
        assertEquals(3, StringUtil.countInstances("hello world", 'l'));
    }

    @Test
    public void capDecimalPlaces_shouldPreserveVerbatimUnderCap() {
        assertEquals("4.65", StringUtil.capDecimalPlaces("4.65", 4));
        assertEquals("5", StringUtil.capDecimalPlaces("5", 4));
        assertEquals("1.1234", StringUtil.capDecimalPlaces("1.1234", 4));
        assertEquals("POSITIVE", StringUtil.capDecimalPlaces("POSITIVE", 4));
        assertEquals("<0.0001", StringUtil.capDecimalPlaces("<0.0001", 4));
    }

    @Test
    public void capDecimalPlaces_shouldCapAtFourDecimals() {
        assertEquals("1.1235", StringUtil.capDecimalPlaces("1.12345678", 4));
        assertEquals("4.6543", StringUtil.capDecimalPlaces("4.654321", 4));
    }
}
