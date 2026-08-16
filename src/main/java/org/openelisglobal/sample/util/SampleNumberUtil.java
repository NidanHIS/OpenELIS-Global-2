package org.openelisglobal.sample.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openelisglobal.common.log.LogEvent;

public final class SampleNumberUtil {

    // Matches YYDDMM-XXXX or YYDDMM-XXXX-N (stored auto-generated form)
    private static final Pattern STORED_PATTERN = Pattern.compile("^(\\d{2})(\\d{4}-\\d{4})(-\\d+)?$");

    // Matches DDMM-XXXX or DDMM-XXXX-N (user-facing display form)
    private static final Pattern DISPLAY_PATTERN = Pattern.compile("^\\d{4}-\\d{4}(-\\d+)?$");

    private SampleNumberUtil() {
    }

    /**
     * Strips 2-digit year prefix (YY) from stored auto-generated format
     * YYDDMM-XXXX. Returns the original string if null or if it does not match the
     * stored auto-generated format.
     */
    public static String toDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            Matcher m = STORED_PATTERN.matcher(raw.trim());
            if (m.matches()) {
                String ddmmXxxx = m.group(2);
                String overflow = m.group(3);
                return ddmmXxxx + (overflow != null ? overflow : "");
            }
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "toDisplay", e.toString());
        }
        return raw;
    }

    /**
     * Prepends current 2-digit year (YY) to display format DDMM-XXXX for storage
     * (e.g. 261608-0001). Returns original string if null or not matching display
     * format.
     */
    public static String toStorage(String display) {
        if (display == null || display.trim().isEmpty()) {
            return display;
        }
        try {
            String trimmed = display.trim();
            if (DISPLAY_PATTERN.matcher(trimmed).matches()) {
                String yy = new SimpleDateFormat("yy").format(new Date());
                return yy + trimmed;
            }
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "toStorage", e.toString());
        }
        return display;
    }

    /**
     * Checks if string is in stored format (YYDDMM-XXXX).
     */
    public static boolean isStoredDailyFormat(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        try {
            return STORED_PATTERN.matcher(s.trim()).matches();
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "isStoredDailyFormat", e.toString());
            return false;
        }
    }

    /**
     * Checks if string is in display format (DDMM-XXXX).
     */
    public static boolean isDisplayDailyFormat(String s) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        try {
            return DISPLAY_PATTERN.matcher(s.trim()).matches();
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "isDisplayDailyFormat", e.toString());
            return false;
        }
    }
}
