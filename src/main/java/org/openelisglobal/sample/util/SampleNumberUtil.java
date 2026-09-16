package org.openelisglobal.sample.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.openelisglobal.common.log.LogEvent;

/**
 * NIDAN daily sample number conversions.
 *
 * <ul>
 * <li>Stored AUTO: {@code YYMMDDxxxx} — 10 digits (e.g. {@code 2609160001}).</li>
 * <li>Display AUTO: {@code xxxx} — 4 digits (e.g. {@code 0001}).</li>
 * <li>Manual: any other string, never transformed.</li>
 * </ul>
 *
 * No Spring, no DB. Every method is null-safe and fail-closed on exceptions.
 */
public final class SampleNumberUtil {

    public static final int LOOKBACK_CALENDAR_DAYS = 3;

    private static final Pattern STORED_AUTO_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern DISPLAY_FORMAT_PATTERN = Pattern.compile("^\\d{4}$");

    private SampleNumberUtil() {
    }

    public static String toDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            String trimmed = raw.trim();
            if (STORED_AUTO_PATTERN.matcher(trimmed).matches()) {
                return trimmed.substring(6);
            }
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "toDisplay", e.toString());
        }
        return raw;
    }

    public static String toStorage(String display) {
        return toStorage(display, new Date());
    }

    public static String toStorage(String display, Date asOf) {
        if (display == null || display.trim().isEmpty()) {
            return display;
        }
        try {
            String trimmed = display.trim();
            if (DISPLAY_FORMAT_PATTERN.matcher(trimmed).matches() && asOf != null) {
                return new SimpleDateFormat("yyMMdd").format(asOf) + trimmed;
            }
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "toStorage", e.toString());
        }
        return display;
    }

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

    public static List<String> storageKeysForLookback(String fourDigit) {
        return storageKeysForLookback(fourDigit, LOOKBACK_CALENDAR_DAYS, new Date());
    }

    public static List<String> storageKeysForLookback(String fourDigit, int calendarDays, Date asOf) {
        try {
            if (!isDisplayFormat(fourDigit) || calendarDays < 1 || asOf == null) {
                return Collections.emptyList();
            }
            String seq = fourDigit.trim();
            Calendar cal = Calendar.getInstance();
            cal.setTime(asOf);
            SimpleDateFormat fmt = new SimpleDateFormat("yyMMdd");
            List<String> keys = new ArrayList<>(calendarDays);
            for (int i = 0; i < calendarDays; i++) {
                if (i > 0) {
                    cal.add(Calendar.DATE, -1);
                }
                keys.add(fmt.format(cal.getTime()) + seq);
            }
            return keys;
        } catch (Exception e) {
            LogEvent.logError("SampleNumberUtil", "storageKeysForLookback", e.toString());
            return Collections.emptyList();
        }
    }
}
