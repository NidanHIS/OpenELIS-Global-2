package org.openelisglobal.ocl;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.hibernate.HibernateException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.configuration.service.ConfigImportLogService;
import org.openelisglobal.configuration.service.DomainConfigurationHandler;
import org.openelisglobal.configuration.service.FieldProvenanceService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.service.PanelItemService;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testconfiguration.action.TestAddControllerUtills;
import org.openelisglobal.testconfiguration.controller.TestAddController;
import org.openelisglobal.testconfiguration.form.TestAddForm;
import org.openelisglobal.testconfiguration.service.TestAddService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for loading OCL (Open Concept Lab) configuration files. Supports ZIP
 * format containing OCL concept collections.
 *
 * OCL ZIP files are expected to contain JSON files with OCL concept
 * definitions. The handler processes these concepts and creates corresponding
 * tests, panels, and dictionaries in OpenELIS.
 */
@Component
public class OclConfigurationHandler implements DomainConfigurationHandler, OclImporter {

    private static final Logger log = LoggerFactory.getLogger(OclConfigurationHandler.class);

    @Value("${org.openelisglobal.ocl.import.default.testsection:Hematology}")
    private String defaultTestSection;

    @Value("${org.openelisglobal.ocl.import.default.sampletype:Whole Blood}")
    private String defaultSampleType;

    @Autowired
    private OclZipImporter oclZipImporter;

    @Autowired
    private ConfigImportLogService configImportLogService;

    @Autowired
    private FieldProvenanceService fieldProvenanceService;

    @Autowired
    private TestAddService testAddService;

    @Autowired
    private TestAddControllerUtills testAddControllerUtills;

    @Autowired
    private PanelService panelService;

    @Autowired
    private PanelItemService panelItemService;

    @Autowired
    private TestService testService;

    @Autowired
    private DisplayListService displayListService;

    @Override
    public String getDomainName() {
        return "ocl";
    }

    @Override
    public String getFileExtension() {
        return "zip";
    }

    @Override
    public int getLoadOrder() {
        return 400; // Load after dictionaries (300) but before higher-level configs
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@code @Transactional} lives here rather than on {@link #performImport}
     * because {@code processConfiguration} calls {@code performImport} internally,
     * and a self-invocation bypasses the Spring proxy so the annotation would never
     * take effect. One transaction spans the whole package: the import either lands
     * completely or not at all.
     *
     * <p>
     * Nothing inside this call graph may swallow a write failure. Spring marks the
     * transaction rollback-only as soon as a write throws, so a swallowed exception
     * would let the remaining concepts keep writing into a doomed transaction and
     * surface only as {@code UnexpectedRollbackException} at commit. Failing fast
     * instead means {@code ConfigurationInitializationService} skips this file's
     * checksum and the next boot retries it.
     */
    @Override
    @Transactional
    public void processConfiguration(InputStream inputStream, String fileName) throws Exception {
        // OCL files are ZIP files, so we need to handle them specially.
        // The ConfigurationInitializationService passes an InputStream, but for ZIP
        // files
        // we need the actual file path to use OclZipImporter. We'll create a temp file
        // from the InputStream and process it.

        File tempFile = null;
        try {
            // Create a temporary file to hold the ZIP contents
            tempFile = File.createTempFile("ocl-", ".zip");
            tempFile.deleteOnExit();

            // Copy InputStream to temp file, hashing as we go so the package can be
            // identified without a second read.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                    fos.write(buffer, 0, bytesRead);
                }
            }
            String checksum = HexFormat.of().formatHex(digest.digest());

            // Durable gate. The framework's properties-file checksum is a fast
            // pre-filter, but it does not survive a power cut, so this is the marker
            // that actually decides. It is read and written inside this method's
            // transaction, so it cannot disagree with the data it guards.
            if (configImportLogService.isAlreadyImported(getDomainName(), fileName, checksum)) {
                log.info("OCL Import: package {} already imported (checksum {}). Skipping.", fileName, checksum);
                return;
            }

            // Process the ZIP file
            List<JsonNode> oclNodes = new ArrayList<>();
            oclZipImporter.importOclZip(tempFile.getAbsolutePath(), oclNodes);
            performImport(oclNodes);

            configImportLogService.recordImport(getDomainName(), fileName, checksum);
            log.info("OCL Import: recorded package {} as imported (checksum {}).", fileName, checksum);
        } finally {
            // Clean up temp file
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Internal method that contains the actual import logic. Made public for use by
     * OclImportInitializer for manual imports.
     *
     * <p>
     * Annotated so the {@link org.openelisglobal.ocl.OclImportInitializer} entry
     * point is transactional too. When reached from {@link #processConfiguration}
     * this simply joins that transaction (propagation REQUIRED).
     */
    @Override
    @Transactional
    public void performImport(List<JsonNode> oclNodes) {
        log.info("OCL Import: Found {} nodes to process.", oclNodes.size());

        int conceptCount = 0;
        OclToOpenElisMapper mapper = new OclToOpenElisMapper(defaultTestSection, defaultSampleType);
        for (JsonNode node : oclNodes) {
            // If the node is a Collection Version, get its concepts array
            if (node.has("concepts") && node.get("concepts").isArray()) {
                log.info("OCL Import: Node has a concepts array of size {}.", node.get("concepts").size());

                // Step 1: upsert test sections from ConvSet concepts BEFORE processing
                // Test concepts so that mapTestSection() can resolve them by name.
                int sectionsProcessed = mapper.upsertTestSections(node);
                log.info("OCL Import: Section pre-pass complete — {} sections created/verified.", sectionsProcessed);

                // Map all concepts in this node to TestAddForms
                List<TestAddForm> testForms = mapper.mapConceptsToTestAddForms(node);

                for (TestAddForm form : testForms) {
                    conceptCount++;
                    log.info("OCL Import: Processing concept #{} - attempting to create test", conceptCount);
                    handleNewTests(form);
                }
                mapLabsetPanels(mapper);
            }
        }
        refreshDisplayLists();
        log.info("OCL Import: Finished processing. Total concepts processed: {}.", conceptCount);
    }

    private void refreshDisplayLists() {
        testService.refreshTestNames();
        displayListService.refreshList(DisplayListService.ListType.SAMPLE_TYPE_ACTIVE);
        displayListService.refreshList(DisplayListService.ListType.SAMPLE_TYPE_INACTIVE);
        displayListService.refreshList(DisplayListService.ListType.PANELS_ACTIVE);
        displayListService.refreshList(DisplayListService.ListType.PANELS_INACTIVE);
        displayListService.refreshList(DisplayListService.ListType.PANELS);
        displayListService.refreshList(DisplayListService.ListType.TEST_SECTION_ACTIVE);
        displayListService.refreshList(DisplayListService.ListType.TEST_SECTION_BY_NAME);
        displayListService.refreshList(DisplayListService.ListType.TEST_SECTION_INACTIVE);
        SpringContext.getBean(TypeOfSampleService.class).clearCache();
    }

    private void mapLabsetPanels(OclToOpenElisMapper mapper) {
        for (JsonNode panel : mapper.getLabSetPanelNodes()) {
            Map<String, String> names = mapper.extractNames(panel);
            String englishName = names.get("englishName");
            Panel dbPanel = panelService.getPanelByName(englishName);
            log.info("Mapping tests for Panel " + englishName);

            if (dbPanel != null) {
                // The lab owns a panel's membership once someone has edited it. Without
                // this guard updatePanelItems() below deletes every row and reinserts the
                // package's list, which is how an unclean shutdown silently reverted
                // Bijaynagar's panels.
                if (fieldProvenanceService.isUserOwned(FieldProvenanceService.ENTITY_PANEL, dbPanel.getId(),
                        FieldProvenanceService.FIELD_PANEL_ITEMS)) {
                    log.info("Panel '{}' membership is lab-owned; leaving it untouched.", englishName);
                    continue;
                }

                List<PanelItem> panelItems = panelItemService.getPanelItemsForPanel(dbPanel.getId());

                List<Test> newTests = new ArrayList<>();
                Set<String> members = mapper.getLabSetMembers(panel);
                log.info("Mapped Lab Set Members: " + members);
                for (String testName : members) {
                    log.info("Adding Test " + testName + " to Panel " + englishName);
                    Test test = testService.getTestByLocalizedName(testName, Locale.ENGLISH);
                    if (test != null) {
                        log.info("Test " + testName + " added to Panel " + englishName);
                        newTests.add(test);
                    }
                }
                try {
                    panelItemService.updatePanelItems(panelItems, dbPanel, false, "1", newTests, true);
                } catch (LIMSRuntimeException e) {
                    LogEvent.logError("OCL import: failed to seed panel items for panel " + englishName, e);
                    throw e;
                }
            }

        }
    }

    public TestAddForm handleNewTests(TestAddForm form) {

        String jsonString = (form.getJsonWad());
        JSONParser parser = new JSONParser();
        JSONObject obj = null;
        try {
            obj = (JSONObject) parser.parse(jsonString);
        } catch (ParseException e) {
            LogEvent.logError("OCL import: unparseable test JSON payload", e);
            throw new LIMSRuntimeException("OCL import: unparseable test JSON payload", e);
        }
        TestAddControllerUtills.TestAddParams testAddParams = testAddControllerUtills.extractTestAddParms(obj, parser);
        List<TestAddController.TestSet> testSets = testAddControllerUtills.createTestSets(testAddParams);
        Localization nameLocalization = testAddControllerUtills.createNameLocalization(testAddParams);
        Localization reportingNameLocalization = testAddControllerUtills.createReportingNameLocalization(testAddParams);
        try {
            testAddService.addTests(testSets, nameLocalization, reportingNameLocalization, "1");
        } catch (HibernateException e) {
            LogEvent.logError("OCL import: failed to add test set from OCL concept", e);
            throw e;
        }
        return form;
    }
}
