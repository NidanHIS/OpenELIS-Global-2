package org.openelisglobal.testconfiguration.service;

import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.service.PanelItemService;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.resultlimit.service.ResultLimitService;
import org.openelisglobal.resultlimits.valueholder.ResultLimit;
import org.openelisglobal.test.event.TestCreatedEvent;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.testconfiguration.controller.TestAddController.TestSet;
import org.openelisglobal.testresult.service.TestResultService;
import org.openelisglobal.testresult.valueholder.TestResult;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.service.TypeOfSampleTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestAddServiceImpl implements TestAddService {

    @Autowired
    private TypeOfSampleTestService typeOfSampleTestService;
    @Autowired
    private LocalizationService localizationService;
    @Autowired
    private PanelItemService panelItemService;
    @Autowired
    private TestService testService;
    @Autowired
    private TestSectionService testSectionService;
    @Autowired
    private ResultLimitService resultLimitService;
    @Autowired
    private TestResultService testResultService;
    @Autowired
    private TypeOfSampleService typeOfSampleService;
    @Autowired
    private PanelService panelService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void addTests(List<TestSet> testSets, Localization nameLocalization, Localization reportingNameLocalization,
            String currentUserId) {
        nameLocalization.setSysUserId(currentUserId);
        localizationService.insert(nameLocalization);
        reportingNameLocalization.setSysUserId(currentUserId);
        localizationService.insert(reportingNameLocalization);

        for (TestSet set : testSets) {
            set.test.setSysUserId(currentUserId);
            set.test.setLocalizedTestName(nameLocalization);
            set.test.setLocalizedReportingName(reportingNameLocalization);
            testService.insert(set.test);

            TestSection testSection = set.test.getTestSection();
            if ("N".equals(testSection.getIsActive())) {
                testSection.setIsActive("Y");
                testSection.setSysUserId(currentUserId);
                testSectionService.update(testSection);
            }

            for (Test test : set.sortedTests) {
                test.setSysUserId(currentUserId);
                testService.update(test);
            }

            set.typeOfSample.setSysUserId(currentUserId);
            typeOfSampleService.update(set.typeOfSample);

            set.sampleTypeTest.setSysUserId(currentUserId);
            set.sampleTypeTest.setTestId(set.test.getId());
            typeOfSampleTestService.insert(set.sampleTypeTest);

            for (PanelItem item : set.panelItems) {
                item.setSysUserId(currentUserId);
                item.setTest(set.test);
                if (item.getPanel() != null && !GenericValidator.isBlankOrNull(item.getPanel().getId())
                        && GenericValidator.isBlankOrNull(item.getSortOrder())) {
                    item.setSortOrder(getNextSortOrderForPanel(item.getPanel().getId()));
                }
                panelItemService.insert(item);
                if (item.getPanel() != null) {
                    Panel panel = item.getPanel();
                    if ("N".equals(panel.getIsActive())) {
                        panel.setIsActive("Y");
                        panel.setSysUserId(currentUserId);
                        panelService.update(panel);
                    }
                }
            }

            for (TestResult testResult : set.testResults) {
                testResult.setSysUserId(currentUserId);
                testResult.setTest(set.test);
                testResultService.insert(testResult);
                if (testResult.getDefault()) {
                    set.test.setDefaultTestResult(testResult);
                }
            }

            for (ResultLimit resultLimit : set.resultLimits) {
                resultLimit.setSysUserId(currentUserId);
                resultLimit.setTestId(set.test.getId());
                resultLimitService.insert(resultLimit);
            }

            // Publish event AFTER all related data is inserted (including result_limits)
            // This ensures integrations (Odoo, FHIR) can access complete test data
            eventPublisher.publishEvent(new TestCreatedEvent(this, set.test));
        }
    }

    private String getNextSortOrderForPanel(String panelId) {
        try {
            List<PanelItem> existingPanelItems = panelItemService.getPanelItemsForPanel(panelId);
            if (existingPanelItems == null || existingPanelItems.isEmpty()) {
                return "1";
            }
            int maxSortOrder = 0;
            for (PanelItem pi : existingPanelItems) {
                if (pi != null && !GenericValidator.isBlankOrNull(pi.getSortOrder())) {
                    try {
                        int order = Integer.parseInt(pi.getSortOrder().trim());
                        if (order > maxSortOrder) {
                            maxSortOrder = order;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return String.valueOf(maxSortOrder + 1);
        } catch (Exception e) {
            return "1";
        }
    }
}
