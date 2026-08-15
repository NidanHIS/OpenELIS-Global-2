package org.openelisglobal.common.provider.validation;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.common.service.AccessionService;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.stereotype.Component;

@Component
public class DailySampleNumberValidator implements IAccessionNumberGenerator {

    private AccessionService accessionService;

    private AccessionService getAccessionService() {
        if (accessionService == null) {
            accessionService = SpringContext.getBean(AccessionService.class);
        }
        return accessionService;
    }

    /**
     * Generates the next daily sample number formatted as DDMM-XXXX (e.g.
     * 1408-0001). Sequence key in accession_number_info uses YYDDMM to guarantee
     * zero cross-year collisions.
     */
    @Override
    public String getNextAvailableAccessionNumber(String programCode, boolean reserve) {
        return getNextAccessionNumber(programCode, reserve);
    }

    @Override
    public String getNextAccessionNumber(String programCode, boolean reserve) {
        try {
            Date now = new Date();
            String prefixKey = new SimpleDateFormat("yyddMM").format(now);
            String dayMonth = new SimpleDateFormat("ddMM").format(now);

            long nextSeq;
            if (reserve) {
                nextSeq = getAccessionService().getNextNumberIncrement(prefixKey, AccessionFormat.DAILY_SAMPLE_NUMBER);
            } else {
                nextSeq = getAccessionService().getNextNumberNoIncrement(prefixKey,
                        AccessionFormat.DAILY_SAMPLE_NUMBER);
            }

            return String.format("%s-%04d", dayMonth, nextSeq);
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "getNextAccessionNumber", e.toString());
            return null;
        }
    }

    @Override
    public boolean needProgramCode() {
        return false;
    }

    @Override
    public ValidationResults validFormat(String sampleNumber, boolean checkDate) throws IllegalArgumentException {
        if (sampleNumber == null || sampleNumber.trim().isEmpty()) {
            return ValidationResults.FORMAT_FAIL;
        }
        // Block SQL-injection / dangerous characters
        if (sampleNumber.matches(".*['\"<>\\[\\](){};:/?!@#$%^&+=].*")) {
            return ValidationResults.FORMAT_FAIL;
        }
        // Accept daily counter format: DDMM-XXXX (e.g. 1508-0001 or 1508-0001-1)
        boolean isDailyFormat = sampleNumber.matches("^\\d{4}-\\d{4}(-\\d+)?$");
        // Accept user-defined alphanumeric: letters, digits, underscores, hyphens, max 20 chars
        boolean isCustom = sampleNumber.matches("^[A-Za-z0-9_\\-]{1,20}$");
        if (!isDailyFormat && !isCustom) {
            return ValidationResults.FORMAT_FAIL;
        }
        return ValidationResults.SUCCESS;
    }

    @Override
    public String getInvalidMessage(ValidationResults results) {
        return "Sample number is already in use.";
    }

    @Override
    public String getInvalidFormatMessage(ValidationResults results) {
        return "Invalid sample number. Use DDMM-XXXX format or alphanumeric only (max 20 chars).";
    }

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

    @Override
    public int getMaxAccessionLength() {
        return 20; // max chars matching VARCHAR(20) DB column
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
    public boolean accessionNumberIsUsed(String accessionNumber, String recordType) {
        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            return false;
        }
        try {
            org.openelisglobal.sample.service.SampleService sampleService = SpringContext
                    .getBean(org.openelisglobal.sample.service.SampleService.class);
            return sampleService.getSampleBySampleNumber(accessionNumber) != null;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "accessionNumberIsUsed", e.toString());
            return false;
        }
    }

    @Override
    public String getPrefix() {
        return new SimpleDateFormat("ddMM").format(new Date()) + "-";
    }
}
