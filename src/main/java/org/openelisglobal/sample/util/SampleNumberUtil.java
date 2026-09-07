package org.openelisglobal.sample.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openelisglobal.common.log.LogEvent;

/**
 * Pure static utility for NIDAN daily sample number format conversions.
 *
 * <p>
 * Two formats exist:
 * <ul>
 * <li><b>Stored (AUTO)</b>: {@code YYMMDDxxxx} — 10 all-digit chars, no
 * separator. Example: {@code 2609060001} (year=26, month=09, day=06, seq=0001).
 * Only auto-generated numbers use this format in the DB.</li>
 * <li><b>Display</b>: {@code DDxxxx} — 6 all-digit chars, no separator.
 * Example: {@code 060001} (day=06, seq=0001). This is what the UI, users, and
 * analyzers see and send.</li>
 * <li><b>Manual</b>: any alphanumeric string up to 20 chars, stored verbatim.
 * Never expanded or transformed by this utility.</li>
 * </ul>
 *
 * <p>
 * This class has no Spring dependencies and no DB access — safe to call
 * anywhere.
 */
public final class SampleNumberUtil {

    /** Matches exactly 10 digits — the stored AUTO format: YYMMDDxxxx */
    private static final Pattern STORED_AUTO_PATTERN = Pattern.compile("^\\d{10}$");

    /** Matches exactly 6 digits — the display format: DDxxxx */
    private static final Pattern DISPLAY_FORMAT_PATTERN = Pattern.compile("^\\d{6}$");

    private SampleNumberUtil() {
        // utility class — no instantiation
    }

    /**
     * Converts a stored AUTO sample number to its display form by stripping the
     * 4-digit {@code YYMM} prefix.
     *
     * <p>
     * Examples:
     * 
     * <pre>
     *   "2609060001" → "060001"   (AUTO stored → display)
     *   "060001"     → "060001"   (already display, pass-through)
     *   "LAB01"      → "LAB01"    (manual, pass-through)
     *   null/blank   → unchanged
     * </pre>
     *
     * @param raw the raw value as stored in the DB
     * @return display form (DDxxxx) if raw is a 10-digit AUTO value, otherwise raw
     *         unchanged
     */
    public static String toDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            String trimmed = raw.trim();
            Matcher m = STORED_AUTO_PATTERN.matcher(trimmed);
            if (m.matches()) {
                // strip first 4 chars (YYMM) → leaves DDxxxx
                return trimmed.substring(4);
            }
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "toDisplay", e.toString());
        }
        return raw;
    }

    /**
     * Converts a display-format AUTO sample number to its stored form by prepending
     * the current 4-digit {@code YYMM} prefix.
     *
     * <p>
     * IMPORTANT: This method is called ONLY for AUTO-generated numbers on save.
     * Manual entries are NEVER passed through this method — they are stored
     * verbatim.
     *
     * <p>
     * Examples:
     * 
     * <pre>
     *   "060001" → "2609060001"  (display → stored, current YYMM prepended)
     *   "LAB01"  → "LAB01"       (not 6-digit, pass-through — caller should not call for manual)
     *   null     → null
     * </pre>
     *
     * @param display the display value (DDxxxx, 6 digits) as seen by users/UI
     * @return stored form (YYMMDDxxxx) if display matches 6-digit format, otherwise
     *         unchanged
     */
    public static String toStorage(String display) {
        if (display == null || display.trim().isEmpty()) {
            return display;
        }
        try {
            String trimmed = display.trim();
            if (DISPLAY_FORMAT_PATTERN.matcher(trimmed).matches()) {
                String yyMM = new SimpleDateFormat("yyMM").format(new Date());
                return yyMM + trimmed;
            }
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "toStorage", e.toString());
        }
        return display;
    }

    /**
     * Returns {@code true} if the given string is in stored AUTO format (exactly 10
     * digits).
     *
     * @param s the string to check
     * @return true if {@code s} matches {@code ^\d{10}$}
     */
    public static boolean isStoredAutoFormat(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        try {
            return STORED_AUTO_PATTERN.matcher(s.trim()).matches();
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "isStoredAutoFormat", e.toString());
            return false;
        }
    }

    /**
     * Returns {@code true} if the given string is in display format (exactly 6
     * digits).
     *
     * @param s the string to check
     * @return true if {@code s} matches {@code ^\d{6}$}
     */
    public static boolean isDisplayFormat(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        try {
            return DISPLAY_FORMAT_PATTERN.matcher(s.trim()).matches();
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "isDisplayFormat", e.toString());
            return false;
        }
    }
}
