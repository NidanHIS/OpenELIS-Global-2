package org.openelisglobal.common.provider.validation;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.common.service.AccessionService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.stereotype.Component;

/**
 * Generates and validates NIDAN daily sample numbers.
 *
 * <p>
 * <b>Display format</b>: {@code xxxx} — 4-digit daily sequence (0001–9999),
 * resets at midnight.
 *
 * <p>
 * <b>Stored format</b>: {@code YYMMDDxxxx} — 10 digits. Sequence key in
 * {@code accession_number_info} is {@code yyMMdd} (one row per calendar day).
 *
 * <p>
 * Manual user entries bypass this class entirely and are stored verbatim.
 */
@Component
public class DailySampleNumberValidator implements IAccessionNumberGenerator, IAccessionNumberValidator {

    private volatile AccessionService accessionService;

    private AccessionService getAccessionService() {
        if (accessionService == null) {
            synchronized (this) {
                if (accessionService == null) {
                    accessionService = SpringContext.getBean(AccessionService.class);
                }
            }
        }
        return accessionService;
    }

    // ── IAccessionNumberGenerator ────────────────────────────────────────────

    /**
     * Returns the next available sample number. Stored format (YYMMDDxxxx). REST
     * applies {@link org.openelisglobal.sample.util.SampleNumberUtil#toDisplay} so
     * the UI sees 4 digits.
     */
    @Override
    public String getNextAvailableAccessionNumber(String programCode, boolean reserve) {
        return getNextAccessionNumber(programCode, reserve);
    }

    /**
     * Core generation method. Sequence key = {@code yyMMdd} (e.g. {@code 260906}).
     * Returns stored format {@code YYMMDDxxxx} (e.g. {@code 2609060001}).
     */
    @Override
    public String getNextAccessionNumber(String programCode, boolean reserve) {
        try {
            Date now = new Date();
            String sequenceKey = new SimpleDateFormat("yyMMdd").format(now);

            long nextSeq;
            if (reserve) {
                nextSeq = getAccessionService().getNextNumberIncrement(sequenceKey,
                        AccessionFormat.DAILY_SAMPLE_NUMBER);
            } else {
                nextSeq = getAccessionService().getNextNumberNoIncrement(sequenceKey,
                        AccessionFormat.DAILY_SAMPLE_NUMBER);
            }

            return String.format("%s%04d", sequenceKey, nextSeq);

        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "getNextAccessionNumber", e.toString());
            return null;
        }
    }

    @Override
    public boolean needProgramCode() {
        return false;
    }

    // ── IAccessionNumberValidator ────────────────────────────────────────────

    /**
     * Validates format and uniqueness. Accepts stored AUTO (10 digits), display
     * AUTO (4 digits), or alphanumeric manual (1–20 chars).
     */
    @Override
    public ValidationResults checkAccessionNumberValidity(String sampleNumber, String recordType, String isRequired,
            String projectFormName) {
        ValidationResults fmt = validFormat(sampleNumber, true);
        if (fmt != ValidationResults.SUCCESS) {
            return fmt;
        }
        if (accessionNumberIsUsed(sampleNumber, recordType)) {
            return ValidationResults.SAMPLE_FOUND;
        }
        return ValidationResults.SUCCESS;
    }

    /**
     * Validates the format of a sample number.
     * <ul>
     * <li>Blocks SQL injection / dangerous characters.</li>
     * <li>Accepts 10-digit stored AUTO format (YYMMDDxxxx).</li>
     * <li>Accepts 4-digit display format (xxxx).</li>
     * <li>Accepts alphanumeric manual entry: {@code [A-Za-z0-9]{1,20}}.</li>
     * </ul>
     */
    @Override
    public ValidationResults validFormat(String sampleNumber, boolean checkDate) throws IllegalArgumentException {
        if (sampleNumber == null || sampleNumber.trim().isEmpty()) {
            return ValidationResults.FORMAT_FAIL;
        }
        String s = sampleNumber.trim();
        // Block dangerous characters
        if (s.matches(".*['\"><\\[\\](){};:/?!@#$%^&+=].*")) {
            return ValidationResults.FORMAT_FAIL;
        }
        // Accept 10-digit stored AUTO format
        boolean isStoredAuto = s.matches("^\\d{10}$");
        // Accept 6-digit display format
        boolean isDisplayAuto = s.matches("^\\d{4}$");
        // Accept alphanumeric manual: letters and digits only, 1-20 chars
        boolean isManual = s.matches("^[A-Za-z0-9]{1,20}$");

        if (!isStoredAuto && !isDisplayAuto && !isManual) {
            return ValidationResults.FORMAT_FAIL;
        }
        return ValidationResults.SUCCESS;
    }

    @Override
    public boolean accessionNumberIsUsed(String accessionNumber, String recordType) {
        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            return false;
        }
        try {
            SampleService sampleService = SpringContext.getBean(SampleService.class);
            return sampleService.getSampleBySampleNumber(accessionNumber.trim()) != null;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "accessionNumberIsUsed", e.toString());
            return false;
        }
    }

    @Override
    public String getInvalidMessage(ValidationResults results) {
        if (results == ValidationResults.FORMAT_FAIL) {
            return getInvalidFormatMessage(results);
        }
        return "Sample number is already in use.";
    }

    @Override
    public String getInvalidFormatMessage(ValidationResults results) {
        return "Invalid sample number. Use 4 digits (e.g. 0001) or alphanumeric only, max 20 chars.";
    }

    @Override
    public int getMaxAccessionLength() {
        return 20;
    }

    @Override
    public int getMinAccessionLength() {
        return 1;
    }

    @Override
    public int getInvarientLength() {
        return 0;
    }

    @Override
    public int getChangeableLength() {
        return 20;
    }

    @Override
    public String getPrefix() {
        return "";
    }
}
