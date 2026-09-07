/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 *
 * <p>Contributor(s): CIRG, University of Washington, Seattle WA.
 */
package org.openelisglobal.common.provider.validation;

import org.openelisglobal.common.exception.LIMSInvalidConfigurationException;
import org.openelisglobal.common.util.ConfigurationListener;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.stereotype.Component;

@Component
public class AccessionNumberValidatorFactory implements ConfigurationListener {

    public enum AccessionFormat {
        MAIN, GENERAL, SITEYEARNUM, PROGRAMNUM, YEARNUM_SIX, YEARNUM_DASH_SEVEN, YEARNUM_SEVEN, UNFORMATTED, ALT_YEAR,
        ALPHANUM, DAILY_SAMPLE_NUMBER
    }

    private AccessionFormat mainAccessionFormat;
    private IAccessionNumberGenerator mainGenerator;

    private IAccessionNumberGenerator getConfiguredMainGenerator() throws LIMSInvalidConfigurationException {

        if (mainGenerator != null) {
            return mainGenerator;
        }

        String accessionFormat = ConfigurationProperties.getInstance()
                .getPropertyValueUpperCase(Property.AccessionFormat);
        if (accessionFormat == null) {
            throw new LIMSInvalidConfigurationException(
                    "AccessionNumberValidatorFactory: Property.AccessionFormat is not configured");
        }

        synchronized (this) {
            if (mainGenerator != null) {
                return mainGenerator;
            }
            if (accessionFormat.equals(AccessionFormat.ALPHANUM.name())) {
                mainGenerator = getAlphanumValidator();
                mainAccessionFormat = AccessionFormat.ALPHANUM;
            } else if (accessionFormat.equals(AccessionFormat.SITEYEARNUM.name())) {
                mainGenerator = getSiteYearValidator();
                mainAccessionFormat = AccessionFormat.SITEYEARNUM;
            } else if (accessionFormat.equals(AccessionFormat.PROGRAMNUM.name())) {
                mainGenerator = getProgramValidator();
                mainAccessionFormat = AccessionFormat.PROGRAMNUM;
            } else if (accessionFormat.equals(AccessionFormat.YEARNUM_SIX.name())) {
                mainGenerator = getYearNumValidator(6, null);
                mainAccessionFormat = AccessionFormat.YEARNUM_SIX;
            } else if (accessionFormat.equals(AccessionFormat.YEARNUM_DASH_SEVEN.name())) {
                mainGenerator = getYearNumValidator(7, '-');
                mainAccessionFormat = AccessionFormat.YEARNUM_DASH_SEVEN;
            } else if (accessionFormat.equals(AccessionFormat.YEARNUM_SEVEN.name())) {
                mainGenerator = getYearNumValidator(7, null);
                mainAccessionFormat = AccessionFormat.YEARNUM_SEVEN;
            }

            if (mainGenerator == null) {
                throw new LIMSInvalidConfigurationException(
                        "AccessionNumberValidatorFactory: Unable to find generator/validator for " + accessionFormat);
            } else {
                return mainGenerator;
            }
        }
    }

    public IAccessionNumberValidator getValidator(AccessionFormat accessionFormat)
            throws LIMSInvalidConfigurationException {

        if (accessionFormat == null) {
            throw new LIMSInvalidConfigurationException(
                    "AccessionNumberValidatorFactory: AccessionFormat cannot be null");
        }

        if (accessionFormat.equals(mainAccessionFormat)) {
            return mainGenerator;
        }
        switch (accessionFormat) {
        case MAIN:
            return getConfiguredMainGenerator();
        case GENERAL:
            return getAllActiveValidator();
        case ALPHANUM:
            return getAlphanumValidator();
        case SITEYEARNUM:
            return getSiteYearValidator();
        case PROGRAMNUM:
            return getProgramValidator();
        case YEARNUM_SIX:
            return getYearNumValidator(6, null);
        case YEARNUM_DASH_SEVEN:
            return getYearNumValidator(7, '-');
        case YEARNUM_SEVEN:
            return getYearNumValidator(7, null);
        case ALT_YEAR:
            return getAltYearValidator();
        case DAILY_SAMPLE_NUMBER:
            return getDailySampleNumberValidator();
        default:
            throw new LIMSInvalidConfigurationException(
                    "AccessionNumberValidatorFactory: Unable to find validator for " + accessionFormat);
        }
    }

    public IAccessionNumberGenerator getGenerator(AccessionFormat accessionFormat)
            throws LIMSInvalidConfigurationException {

        if (accessionFormat == null) {
            throw new LIMSInvalidConfigurationException(
                    "AccessionNumberValidatorFactory: AccessionFormat cannot be null");
        }

        if (accessionFormat.equals(mainAccessionFormat)) {
            return mainGenerator;
        }
        switch (accessionFormat) {
        case MAIN:
            return getConfiguredMainGenerator();
        case ALPHANUM:
            return getAlphanumValidator();
        case SITEYEARNUM:
            return getSiteYearValidator();
        case PROGRAMNUM:
            return getProgramValidator();
        case YEARNUM_SIX:
            return getYearNumValidator(6, null);
        case YEARNUM_DASH_SEVEN:
            return getYearNumValidator(7, '-');
        case YEARNUM_SEVEN:
            return getYearNumValidator(7, null);
        case ALT_YEAR:
            return getAltYearValidator();
        case DAILY_SAMPLE_NUMBER:
            return getDailySampleNumberValidator();
        case GENERAL:
            throw new LIMSInvalidConfigurationException(
                    "AccessionNumberValidatorFactory: ALL_ACTIVE unable to be used as a generator ");
        default:
            throw new LIMSInvalidConfigurationException(
                    "AccessionNumberValidatorFactory: Unable to find Generator for " + accessionFormat);
        }
    }

    @SuppressWarnings("unused")
    private IAccessionNumberGenerator getDigitAccessionValidator(int length) {
        return new DigitAccessionValidator(length);
    }

    private IAccessionNumberGenerator getYearNumValidator(int length, Character separator) {
        return new YearNumAccessionValidator(length, separator);
    }

    private IAccessionNumberGenerator getAlphanumValidator() {
        return new AlphanumAccessionValidator();
    }

    private IAccessionNumberGenerator getSiteYearValidator() {
        return new SiteYearAccessionValidator();
    }

    private IAccessionNumberGenerator getAltYearValidator() {
        return new AltYearAccessionValidator();
    }

    private IAccessionNumberValidator getAllActiveValidator() throws LIMSInvalidConfigurationException {
        return new GeneralAccessionNumberValidator();
    }

    private IAccessionNumberGenerator getProgramValidator() {
        return new ProgramAccessionValidator();
    }

    public DailySampleNumberValidator getDailySampleNumberValidator() {
        try {
            return SpringContext.getBean(DailySampleNumberValidator.class);
        } catch (Exception e) {
            return new DailySampleNumberValidator();
        }
    }

    @Override
    public void refreshConfiguration() {
        mainAccessionFormat = null;
        mainGenerator = null;
    }
}
