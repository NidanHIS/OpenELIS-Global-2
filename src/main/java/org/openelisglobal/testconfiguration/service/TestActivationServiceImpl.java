package org.openelisglobal.testconfiguration.service;

import java.util.List;
import org.openelisglobal.configuration.service.FieldProvenanceService;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestActivationServiceImpl implements TestActivationService {

    @Autowired
    private TypeOfSampleService typeOfSampleService;
    @Autowired
    private TestService testService;

    @Autowired
    private FieldProvenanceService fieldProvenanceService;

    @Override
    @Transactional
    public void updateAll(List<Test> deactivateTests, List<Test> activateTests,
            List<TypeOfSample> deactivateSampleTypes, List<TypeOfSample> activateSampleTypes) {

        // Activating or retiring a test by hand is a lab decision, so the package
        // stops managing that flag for these tests.
        for (Test test : deactivateTests) {
            testService.update(test);
            fieldProvenanceService.markUserOwned(FieldProvenanceService.ENTITY_TEST, test.getId(),
                    List.of(FieldProvenanceService.FIELD_IS_ACTIVE));
        }

        for (Test test : activateTests) {
            testService.update(test);
            fieldProvenanceService.markUserOwned(FieldProvenanceService.ENTITY_TEST, test.getId(),
                    List.of(FieldProvenanceService.FIELD_IS_ACTIVE));
        }

        for (TypeOfSample typeOfSample : deactivateSampleTypes) {
            typeOfSampleService.update(typeOfSample);
        }

        for (TypeOfSample typeOfSample : activateSampleTypes) {
            typeOfSampleService.update(typeOfSample);
        }

        if (!deactivateSampleTypes.isEmpty() || !activateSampleTypes.isEmpty() || !deactivateTests.isEmpty()
                || !activateTests.isEmpty()) {
            SpringContext.getBean(TypeOfSampleService.class).clearCache();
        }
    }
}
