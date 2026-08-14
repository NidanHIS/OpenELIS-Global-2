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
        if (sampleNumber.matches(".*['\"<>\\[\\](){};:/?!@#$%^&+=].*")) {
            return ValidationResults.FORMAT_FAIL;
        }
        // Pattern: DDMM-XXXX (e.g. 1408-0001 or 1408-0001-1)
        if (!sampleNumber.matches("^\\d{4}-\\d{4}(-\\d+)?$")) {
            return ValidationResults.FORMAT_FAIL;
        }
        return ValidationResults.SUCCESS;
    }

    @Override
    public String getInvalidMessage(ValidationResults results) {
        return "Invalid sample number. Expected format: DDMM-XXXX (e.g. 1408-0001) and must not already be in use.";
    }

    @Override
    public String getInvalidFormatMessage(ValidationResults results) {
        return "Invalid sample number format. Expected format: DDMM-XXXX (e.g. 1408-0001)";
    }

    @Override
    public ValidationResults checkAccessionNumberValidity(String sampleNumber, String recordType, String isRequired,
            String projectFormName) {
        return validFormat(sampleNumber, true);
    }

    @Override
    public int getMaxAccessionLength() {
        return 9; // DDMM-XXXX
    }

    @Override
    public int getMinAccessionLength() {
        return 9;
    }

    @Override
    public int getInvarientLength() {
        return 5; // DDMM-
    }

    @Override
    public int getChangeableLength() {
        return 4; // XXXX
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
