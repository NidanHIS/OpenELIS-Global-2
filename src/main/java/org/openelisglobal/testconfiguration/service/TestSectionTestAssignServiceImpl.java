package org.openelisglobal.testconfiguration.service;

import java.util.List;
import org.openelisglobal.configuration.service.FieldProvenanceService;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestSection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestSectionTestAssignServiceImpl implements TestSectionTestAssignService {

    @Autowired
    private TestService testService;
    @Autowired
    private TestSectionService testSectionService;

    @Autowired
    private FieldProvenanceService fieldProvenanceService;

    @Override
    @Transactional
    public void updateTestAndTestSections(Test test, TestSection testSection, TestSection deActivateTestSection,
            boolean updateTestSection) {
        testService.update(test);
        // Moving a test between departments is a lab decision. Record it so the OCL
        // importer stops managing this test's section and cannot move it back.
        fieldProvenanceService.markUserOwned(FieldProvenanceService.ENTITY_TEST, test.getId(),
                List.of(FieldProvenanceService.FIELD_TEST_SECTION));

        if (updateTestSection) {
            testSectionService.update(testSection);
        }

        if (deActivateTestSection != null) {
            testSectionService.update(deActivateTestSection);
        }
    }
}
