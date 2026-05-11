// File: OclToOpenElisMapper.java (Corrected field name "concept class")
package org.openelisglobal.ocl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.dictionarycategory.service.DictionaryCategoryService;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.ocl.service.OclMappingService;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.systemmodule.valueholder.SystemModule;
import org.openelisglobal.systemusermodule.valueholder.RoleModule;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.testconfiguration.form.TestAddForm;
import org.openelisglobal.testconfiguration.service.PanelCreateService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultService;
import org.openelisglobal.typeoftestresult.valueholder.TypeOfTestResult;
import org.openelisglobal.unitofmeasure.service.UnitOfMeasureService;
import org.openelisglobal.unitofmeasure.valueholder.UnitOfMeasure;

public class OclToOpenElisMapper {
    private static final Log log = LogFactory.getLog(OclToOpenElisMapper.class);

    private String defaultTestSection;
    private String defaultSampleType;
    private OclMappingService oclMappingService;
    private JsonNode rootNode;
    private String systemUserId = "1";
    private Set<JsonNode> labSetPanelNodes;

    /**
     * Built once per import run by {@link #buildLabSetToSectionLookup(JsonNode)}.
     *
     * Key   = LabSet concept id (String, e.g. "150")
     * Value = display_name of the real (non-meta) ConvSet that owns that LabSet
     *         (e.g. "Haematology")
     *
     * Used by {@link #mapTestSection} Priority 1.5: when a test has no direct
     * ConvSet parent, we walk Test → LabSet → ConvSet to resolve the section.
     */
    private Map<String, String> labSetToSectionName = new HashMap<>();

    public Set<JsonNode> getLabSetPanelNodes() {
        return labSetPanelNodes;
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private TypeOfTestResultService typeOfTestResultService = SpringContext.getBean(TypeOfTestResultService.class);
    private TestSectionService testSectionService = SpringContext.getBean(TestSectionService.class);
    private UnitOfMeasureService uomSerivice = SpringContext.getBean(UnitOfMeasureService.class);
    private TypeOfSampleService typeOfSampleService = SpringContext.getBean(TypeOfSampleService.class);
    private DictionaryService dictionaryService = SpringContext.getBean(DictionaryService.class);
    private DictionaryCategoryService dictionaryCategoryService = SpringContext
            .getBean(DictionaryCategoryService.class);
    private LocalizationService localizationService = SpringContext.getBean(LocalizationService.class);
    private RoleService roleService = SpringContext.getBean(RoleService.class);
    private PanelCreateService panelCreateService = SpringContext.getBean(PanelCreateService.class);
    private PanelService panelService = SpringContext.getBean(PanelService.class);
    private TestService testService = SpringContext.getBean(TestService.class);

    public OclToOpenElisMapper(String defaultTestSection, String defaultSampleType) {
        this.defaultTestSection = defaultTestSection;
        this.defaultSampleType = defaultSampleType;
        this.oclMappingService = SpringContext.getBean(OclMappingService.class);
    }

    private static final Set<String> SUPPORTED_DATATYPES = Set.of("NUMERIC", "TEXT", "CODED", "N/A", "NONE");
    private static final Set<String> ALLOWED_CONCEPT_CLASSES = Set.of("TEST");
    private static final Set<String> LABSET_CONCEPT_CLASSES = Set.of("LABSET");
    private static final Set<String> CONVSET_CONCEPT_CLASSES = Set.of("CONVSET");
    private static final Set<String> NUMERIC_DATA_TYPES = Set.of("NUMERIC");
    private static final Set<String> CODED_DATA_TYPES = Set.of("CODED");
    private static final Set<String> NONE_DATATYPES = Set.of("NONE", "N/A");

    /**
     * ConvSet display_names that are organisational groupings in OCL, NOT real
     * laboratory departments. These must never be created as TestSection rows.
     *
     * "Department"          — root container for the 11 real sections
     * "Tests Orderability"  — flat list of every orderable test/panel
     * "All Orderable Tests" — contains "Tests Orderability"
     */
    private static final Set<String> META_CONVSET_NAMES = Set.of(
            "Department",
            "Tests Orderability",
            "All Orderable Tests"
    );

    // Map OCL data types to OpenELIS result type IDs
    private static final Map<String, String> RESULT_TYPE_MAPPING = new HashMap<>();
    static {
        RESULT_TYPE_MAPPING.put("NUMERIC", "N");
        RESULT_TYPE_MAPPING.put("CODED", "D");
        RESULT_TYPE_MAPPING.put("TEXT", "R");
        RESULT_TYPE_MAPPING.put("N/A", "R"); // Free text result
        RESULT_TYPE_MAPPING.put("NONE", "R"); // Free text result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 — Test-section pre-pass
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Idempotently creates or re-activates OpenELIS TestSection rows from OCL
     * ConvSet concepts.
     *
     * <p><b>Must be called BEFORE {@link #mapConceptsToTestAddForms}</b> so that
     * every section exists in the database by the time individual Test concepts
     * try to resolve their section via {@code mapTestSection()}.
     *
     * <p>Rules:
     * <ul>
     *   <li>ConvSets whose {@code display_name} is in {@link #META_CONVSET_NAMES}
     *       are skipped — they are organisational groupings, not lab departments.</li>
     *   <li>Idempotency key: English name via
     *       {@link TestSectionService#getTestSectionByName(String)}.
     *       <ul>
     *         <li>Found → ensure {@code is_active = 'Y'}; update only if dirty.</li>
     *         <li>Not found → insert with a new Localization row.</li>
     *       </ul>
     *   </li>
     *   <li>Any per-concept exception is caught and logged; the loop continues so
     *       one bad concept cannot abort the entire pre-pass.</li>
     * </ul>
     *
     * @param rootNode the root OCL JSON node (must contain a {@code concepts} array)
     * @return number of sections created or verified/updated
     */
    public int upsertTestSections(JsonNode rootNode) {
        JsonNode concepts = rootNode.get("concepts");
        if (concepts == null || !concepts.isArray()) {
            log.warn("OCL upsertTestSections: no 'concepts' array found — skipping section pre-pass.");
            return 0;
        }

        int created = 0;
        int verified = 0;
        int skipped = 0;

        for (JsonNode concept : concepts) {
            String conceptClass = getText(concept, "concept_class");

            // Only process ConvSet concepts
            if (conceptClass == null
                    || !CONVSET_CONCEPT_CLASSES.contains(conceptClass.toUpperCase())) {
                continue;
            }

            Map<String, String> names = extractNames(concept);
            String englishName = names.get("englishName");
            String frenchName  = names.get("frenchName");

            // Guard: must have a non-blank name
            if (StringUtils.isBlank(englishName)) {
                log.warn("OCL upsertTestSections: ConvSet id=" + getText(concept, "id") + " has no resolvable English name — skipping.");
                skipped++;
                continue;
            }

            // Guard: skip meta/grouping ConvSets
            if (META_CONVSET_NAMES.contains(englishName)) {
                log.info("OCL upsertTestSections: skipping meta ConvSet '" + englishName + "'.");
                skipped++;
                continue;
            }

            try {
                TestSection existing = testSectionService.getTestSectionByName(englishName);

                if (existing != null) {
                    // Already exists — only touch is_active if it is off
                    if (!"Y".equals(existing.getIsActive())) {
                        existing.setIsActive("Y");
                        existing.setSysUserId(systemUserId);
                        testSectionService.update(existing);
                        log.info("OCL upsertTestSections: re-activated section '" + englishName + "'.");
                    } else {
                        log.info("OCL upsertTestSections: section '" + englishName + "' already active — no change.");
                    }
                    verified++;

                } else {
                    // Does not exist — create Localization first (NOT NULL FK), then TestSection
                    Localization localization = new Localization();
                    localization.setEnglish(englishName);
                    // frenchName falls back to englishName when absent (extractNames guarantees this)
                    localization.setFrench(frenchName);
                    localization.setDescription("test section name");
                    localization.setSysUserId(systemUserId);
                    String localizationId = localizationService.insert(localization);
                    localization.setId(localizationId);

                    TestSection section = new TestSection();
                    section.setTestSectionName(englishName);
                    section.setDescription(englishName);
                    section.setIsActive("Y");
                    section.setIsExternal("N");
                    section.setLocalization(localization);
                    section.setSysUserId(systemUserId);
                    testSectionService.insert(section);

                    log.info("OCL upsertTestSections: created section '" + englishName + "'.");
                    created++;
                }

            } catch (Exception e) {
                log.error("OCL upsertTestSections: failed to upsert section '" + englishName + "' — skipping this entry. Error: " + e.getMessage(), e);
                skipped++;
            }
        }

        log.info("OCL upsertTestSections complete: created=" + created + ", verified=" + verified + ", skipped=" + skipped + ".");
        return created + verified;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 onwards — concept mapping (unchanged for now)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the {@link #labSetToSectionName} lookup once per import run.
     *
     * <p>Algorithm — single pass over all CONCEPT-SET mappings:
     * <ul>
     *   <li>For every mapping where {@code from} is a real (non-meta) ConvSet
     *       and {@code to} is a LabSet, record
     *       {@code labSetId → convSetName}.</li>
     *   <li>{@code putIfAbsent} — first ConvSet owner wins (deterministic;
     *       no LabSet belongs to two real sections in TTH v0.4).</li>
     * </ul>
     *
     * <p>Result for TTH v0.4: 13 entries covering all LabSets that have a
     * real section parent. The 3 LabSets that are only under "Tests
     * Orderability" (Basic Serology, Blood Banking, Renal Function Test)
     * will not appear in the map — their tests fall through to the default.
     *
     * @param rootNode the root OCL JSON node
     */
    private void buildLabSetToSectionLookup(JsonNode rootNode) {
        labSetToSectionName = new HashMap<>();

        JsonNode concepts = rootNode.get("concepts");
        JsonNode mappings  = rootNode.get("mappings");
        if (concepts == null || !concepts.isArray()
                || mappings == null || !mappings.isArray()) {
            log.warn("OCL buildLabSetToSectionLookup: missing concepts or mappings array — lookup will be empty.");
            return;
        }

        // Build id → class and id → name maps for O(1) lookup during the pass
        Map<String, String> idToClass = new HashMap<>();
        Map<String, String> idToName  = new HashMap<>();
        for (JsonNode c : concepts) {
            String id   = getText(c, "id");
            String cls  = getText(c, "concept_class");
            String name = getText(c, "display_name");
            if (id != null) {
                idToClass.put(id, cls  != null ? cls.toUpperCase()  : "");
                idToName .put(id, name != null ? name               : "");
            }
        }

        // Single pass: ConvSet → LabSet edges only
        for (JsonNode mapping : mappings) {
            String mapType = getText(mapping, "map_type");
            if (!"CONCEPT-SET".equalsIgnoreCase(mapType)) {
                continue;
            }
            String fromId = getText(mapping, "from_concept_code");
            String toId   = getText(mapping, "to_concept_code");
            if (fromId == null || toId == null) {
                continue;
            }
            // from must be a real (non-meta) ConvSet
            if (!"CONVSET".equals(idToClass.get(fromId))) {
                continue;
            }
            String convSetName = idToName.get(fromId);
            if (convSetName == null || META_CONVSET_NAMES.contains(convSetName)) {
                continue;
            }
            // to must be a LabSet
            if (!"LABSET".equals(idToClass.get(toId))) {
                continue;
            }
            labSetToSectionName.putIfAbsent(toId, convSetName);
            log.debug("OCL section lookup: LabSet " + toId + " ('" + idToName.get(toId) + "') → section '" + convSetName + "'");
        }

        log.info("OCL buildLabSetToSectionLookup: " + labSetToSectionName.size() + " LabSet→section entries built.");
    }

    /**
     * Maps OCL concepts to TestAddForm objects ready for submission, applying
     * filters for datatype and concept_class.
     * 
     * @param rootNode The root JSON node from OCL export, containing a "concepts"
     *                 array.
     * @return A list of TestAddForm objects that pass the filters.
     */
    public List<TestAddForm> mapConceptsToTestAddForms(JsonNode rootNode) {
        try {
            List<TestAddForm> forms = new ArrayList<>();
            labSetPanelNodes = new HashSet<>();
            this.rootNode = rootNode;

            // Build the LabSet→section lookup used by mapTestSection() Priority 1.5.
            // Done once here so the per-test call is O(mappings-per-test), not O(all-mappings²).
            buildLabSetToSectionLookup(rootNode);

            // Validate root node structure - accept both Source Version and Collection
            // Version
            String oclType = getText(rootNode, "type");
            if (!rootNode.has("type") || (!"Collection Version".equals(oclType) && !"Source Version".equals(oclType))) {
                log.error("Invalid OCL export format. Expected Collection Version or Source Version type. Got: "
                        + oclType);
                return forms;
            }
            log.info("Processing OCL export of type: " + oclType);

            // Handle concepts array from OCL export
            JsonNode concepts = rootNode.get("concepts");
            if (concepts != null && concepts.isArray()) {
                log.info("Processing " + concepts.size() + " concepts from collection: "
                        + getText(rootNode, "full_name"));

                for (JsonNode conceptNode : concepts) {
                    String conceptId = getText(conceptNode, "id");
                    String displayName = getText(conceptNode, "display_name");
                    log.info("Processing concept: " + displayName + " (ID: " + conceptId + ")");

                    TestAddForm form = mapSingleConceptToForm(conceptNode);
                    if (form != null) {
                        forms.add(form);
                    }
                }
            } else {
                log.error("Expected 'concepts' array in OCL export");
            }

            return forms;
        } catch (Exception e) {
            log.error("Error mapping OCL concepts to TestAddForm: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Maps a single OCL concept to a TestAddForm, applying filters and specific
     * mappings.
     * 
     * @param concept The JSON node representing a single OCL concept.
     * @return A TestAddForm object if the concept passes all filters, otherwise
     *         null.
     */
    private TestAddForm mapSingleConceptToForm(JsonNode concept) {
        try {
            String conceptId = getText(concept, "id");
            String externalId = getText(concept, "external_id"); // OpenMRS UUID - this is what external orders
                                                                 // reference
            String displayName = getText(concept, "display_name");
            String dataType = getText(concept, "datatype");
            String conceptClass = getText(concept, "concept_class");
            Map<String, String> names = extractNames(concept);
            String englishName = names.get("englishName");
            String frenchName = names.get("frenchName");
            String description = names.get("description");
            // Use englishName as fallback when description is empty to prevent duplicate
            // empty description collision
            if (StringUtils.isBlank(description)) {
                description = englishName;
            }
            String loinc = getLoinc(conceptId);
            // Log detailed concept information for debugging
            logConceptDetails(concept);

            if (dataType != null && conceptClass != null && LABSET_CONCEPT_CLASSES.contains(conceptClass.toUpperCase())
                    && NONE_DATATYPES.contains(dataType.toUpperCase())) {

                // ── Panel upsert: GUID-first → name-fallback → create ─────────────────
                Panel existingPanel = null;

                // Step 1: GUID lookup — canonical match regardless of name changes
                if (StringUtils.isNotBlank(externalId)) {
                    existingPanel = panelService.getPanelByGUID(externalId);
                    if (existingPanel != null) {
                        log.info("OCL panel upsert: found by GUID '" + externalId + "' → panel '" + englishName + "'.");
                    }
                }

                // Step 2: name fallback — panel existed before OCL had GUIDs
                if (existingPanel == null) {
                    Panel probe = createPanel(englishName, description, systemUserId, loinc, externalId);
                    existingPanel = panelService.getPanelByName(probe);
                    if (existingPanel != null) {
                        log.info("OCL panel upsert: found by name '" + englishName + "' (GUID lookup missed).");
                    }
                }

                if (existingPanel != null) {
                    // Update all mutable fields from OCL
                    boolean needsUpdate = false;

                    if (StringUtils.isNotBlank(externalId) && !externalId.equals(existingPanel.getGuid())) {
                        log.info("OCL panel upsert: stamping GUID '" + externalId + "' on panel '" + englishName + "'.");
                        existingPanel.setGuid(externalId);
                        needsUpdate = true;
                    }
                    if (StringUtils.isNotBlank(loinc) && !loinc.equals(existingPanel.getLoinc())) {
                        existingPanel.setLoinc(loinc);
                        needsUpdate = true;
                    }
                    // Sync active/retired flag from OCL
                    boolean retired = Boolean.parseBoolean(getText(concept, "retired"));
                    String expectedActive = retired ? "N" : "Y";
                    if (!expectedActive.equals(existingPanel.getIsActive())) {
                        existingPanel.setIsActive(expectedActive);
                        needsUpdate = true;
                    }

                    if (needsUpdate) {
                        existingPanel.setSysUserId(systemUserId);
                        panelService.update(existingPanel);
                        log.info("OCL panel upsert: updated panel '" + englishName + "'.");
                    } else {
                        log.info("OCL panel upsert: panel '" + englishName + "' already up-to-date — no change.");
                    }

                } else {
                    // Step 3: create — panel does not exist at all
                    Localization localization = createLocalization(frenchName, englishName, "create panel",
                            systemUserId);

                    SystemModule workplanModule = createSystemModule("Workplan", englishName, systemUserId);
                    SystemModule resultModule = createSystemModule("LogbookResults", englishName, systemUserId);
                    SystemModule validationModule = createSystemModule("ResultValidation", englishName, systemUserId);

                    Role resultsEntryRole = roleService.getRoleByName(Constants.ROLE_RESULTS);
                    Role validationRole = roleService.getRoleByName(Constants.ROLE_VALIDATION);

                    RoleModule workplanResultModule = createRoleModule(systemUserId, workplanModule, resultsEntryRole);
                    RoleModule resultResultModule = createRoleModule(systemUserId, resultModule, resultsEntryRole);
                    RoleModule validationValidationModule = createRoleModule(systemUserId, validationModule,
                            validationRole);
                    TypeOfSample typeOfSample = getTypeOfSample(concept);
                    Panel newPanel = createPanel(englishName, description, systemUserId, loinc, externalId);
                    panelCreateService.insert(localization, newPanel, workplanModule, resultModule, validationModule,
                            workplanResultModule, resultResultModule, validationValidationModule, typeOfSample.getId(),
                            systemUserId);
                    log.info("OCL panel upsert: created new panel '" + englishName + "'.");
                }

                getLabSetPanelNodes().add(concept);
                return null;
            }

            // Datatype filtering: Only map specific test types
            if (dataType == null || !SUPPORTED_DATATYPES.contains(dataType.toUpperCase())) {
                log.info("Skipping concept " + conceptId + " due to unsupported datatype: " + dataType);
                return null;
            }

            if (conceptClass == null || !ALLOWED_CONCEPT_CLASSES.contains(conceptClass.toUpperCase())) {
                log.info("Skipping concept " + conceptId + " (name: " + displayName + ") "
                        + "due to unsupported concept_class: " + conceptClass);
                return null;
            }

            // ── Test upsert: GUID-first → name-fallback → create ──────────────────────
            Test dbTest = null;

            // Step 1: GUID lookup — canonical match regardless of name changes
            if (StringUtils.isNotBlank(externalId)) {
                dbTest = testService.getTestByGUID(externalId);
                if (dbTest != null) {
                    log.info("OCL test upsert: found by GUID '" + externalId + "' → test '" + englishName + "'.");
                }
            }

            // Step 2: name fallback — test existed before OCL had GUIDs, or GUID not yet set
            if (dbTest == null) {
                dbTest = testService.getTestByLocalizedName(englishName, Locale.ENGLISH);
                if (dbTest != null) {
                    log.info("OCL test upsert: found by name '" + englishName + "' (GUID lookup missed).");
                }
            }

            if (dbTest != null) {
                // Update all mutable fields from OCL
                boolean needsUpdate = false;

                // Stamp GUID if missing or changed
                if (StringUtils.isNotBlank(externalId) && !externalId.equals(dbTest.getGuid())) {
                    log.info("OCL test upsert: stamping GUID '" + externalId + "' on test '" + englishName + "'.");
                    dbTest.setGuid(externalId);
                    needsUpdate = true;
                }

                // Update LOINC
                if (StringUtils.isNotBlank(loinc) && !loinc.equals(dbTest.getLoinc())) {
                    dbTest.setLoinc(loinc);
                    needsUpdate = true;
                }

                // Update section — resolve via P1/P1.5/P2/P3/P4/P5
                TestSection resolvedSection = resolveTestSection(concept);
                if (resolvedSection != null) {
                    TestSection currentSection = dbTest.getTestSection();
                    if (currentSection == null
                            || !resolvedSection.getId().equals(currentSection.getId())) {
                        log.info("OCL test upsert: updating section for '" + englishName + "': '"
                                + (currentSection != null ? currentSection.getTestSectionName() : "null")
                                + "' → '" + resolvedSection.getTestSectionName() + "'.");
                        dbTest.setTestSection(resolvedSection);
                        needsUpdate = true;
                    }
                }

                // Update UOM — resolve or create
                JsonNode extrasNode = concept.get("extras");
                if (extrasNode != null && extrasNode.has("units")) {
                    String units = getText(extrasNode, "units");
                    if (StringUtils.isNotBlank(units)) {
                        // resolveOrCreateUom returns a session-managed entity — safe to assign
                        UnitOfMeasure resolvedUom = resolveOrCreateUom(units);
                        if (resolvedUom != null) {
                            UnitOfMeasure currentUom = dbTest.getUnitOfMeasure();
                            if (currentUom == null || !resolvedUom.getId().equals(currentUom.getId())) {
                                dbTest.setUnitOfMeasure(resolvedUom);
                                needsUpdate = true;
                            }
                        }
                    }
                }

                // Sync active/retired flag
                boolean retired = Boolean.parseBoolean(getText(concept, "retired"));
                String expectedActive = retired ? "N" : "Y";
                if (!expectedActive.equals(dbTest.getIsActive())) {
                    dbTest.setIsActive(expectedActive);
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    dbTest.setSysUserId(systemUserId);
                    testService.update(dbTest);
                    log.info("OCL test upsert: updated test '" + englishName + "'.");
                } else {
                    log.info("OCL test upsert: test '" + englishName + "' already up-to-date — no change.");
                }
                return null; // existing test handled — no TestAddForm needed
            }

            // Log concept class information
            log.info("Processing concept ID: " + conceptId + ", Name: " + displayName);
            log.info("Concept Class: " + conceptClass + ", Data Type: " + dataType);

            TestAddForm form = new TestAddForm();

            // Create the JSON structure expected by TestAddRestController
            ObjectNode jsonWad = objectMapper.createObjectNode();

            // Map all required fields in the exact format
            mapTestNames(englishName, frenchName, jsonWad);
            mapTestSection(concept, jsonWad); // Hardcoded to Hematology
            mapPanels(concept, jsonWad);
            mapUnits(concept, jsonWad);
            mapLoinc(loinc, jsonWad);
            mapGuid(externalId, jsonWad); // Pass external_id (OpenMRS UUID) as test GUID
            mapResultType(concept, jsonWad);
            mapOrderableFlags(concept, jsonWad);
            mapSampleTypes(concept, jsonWad);
            mapNumericValidation(concept, jsonWad);
            mapResultLimits(concept, jsonWad);
            mapDictionaryResults(concept, jsonWad);
            // Convert to JSON string and set in form
            String jsonWadString = objectMapper.writeValueAsString(jsonWad);
            form.setJsonWad(jsonWadString);

            log.info("Successfully mapped OCL concept " + conceptId + " (external_id: " + externalId
                    + ") to TestAddForm");
            log.debug("Generated JSON: " + jsonWadString);

            return form;
        } catch (Exception e) {
            log.error("Error mapping single OCL concept: " + e.getMessage(), e);
            return null;
        }
    }

    private void mapTestNames(String englishName, String frenchName, ObjectNode jsonWad) {
        jsonWad.put("testNameEnglish", englishName != null ? englishName : "");
        jsonWad.put("testNameFrench", frenchName != null ? frenchName : "");
        jsonWad.put("testReportNameEnglish", englishName != null ? englishName : "");
        jsonWad.put("testReportNameFrench", frenchName != null ? frenchName : "");
    }

    public Map<String, String> extractNames(JsonNode concept) {
        String englishName = null;
        String frenchName = null;
        String description = null;

        // Get display_name as initial English name
        englishName = getText(concept, "display_name");

        // Process names array for best matching names
        JsonNode names = concept.get("names");
        if (names != null && names.isArray()) {
            for (JsonNode nameNode : names) {
                String locale = getText(nameNode, "locale");
                String name = getText(nameNode, "name");
                String nameType = getText(nameNode, "name_type");

                if (name != null && "FULLY_SPECIFIED".equals(nameType)) {
                    if ("en".equals(locale)) {
                        englishName = name; // overwrite with preferred English name
                    } else if ("fr".equals(locale)) {
                        frenchName = name;
                    }
                } else if (name != null) {
                    if ("en".equals(locale) && englishName == null) {
                        englishName = name;
                    } else if ("fr".equals(locale) && frenchName == null) {
                        frenchName = name;
                    }
                }
            }
        }

        // Fallbacks for English
        if (englishName == null) {
            JsonNode descriptions = concept.get("descriptions");
            if (descriptions != null && descriptions.isArray() && descriptions.size() > 0) {
                description = getText(descriptions.get(0), "description");
                englishName = description;

            }
            if (englishName == null) {
                englishName = getText(concept, "id");
            }
        }

        if (frenchName == null) {
            frenchName = englishName;
        }

        Map<String, String> result = new HashMap<>();
        result.put("englishName", englishName);
        result.put("frenchName", frenchName);
        result.put("description", description != null ? description : "");
        return result;
    }

    private void mapTestSection(JsonNode concept, ObjectNode jsonWad) {
        TestSection testSection = resolveTestSection(concept);
        String testSectionId = (testSection != null) ? testSection.getId() : null;
        if (testSectionId == null) {
            log.warn("OCL mapTestSection: no section resolved for test id='" + getText(concept, "id")
                    + "' name='" + getText(concept, "display_name") + "' — testSection will be null.");
        }
        jsonWad.put("testSection", testSectionId);
    }

    /**
     * Resolves the OpenELIS {@link TestSection} for an OCL Test concept.
     *
     * <p>Priority chain:
     * <ol>
     *   <li><b>P1</b> — direct CONCEPT-SET mapping: ConvSet → this Test</li>
     *   <li><b>P1.5</b> — indirect chain: LabSet → this Test, where LabSet is
     *       owned by a real ConvSet (pre-built in {@link #labSetToSectionName})</li>
     *   <li><b>P2</b> — OCL {@code extras.test_section} field</li>
     *   <li><b>P3</b> — {@code ocl-test-mapping.json} manual override</li>
     *   <li><b>P4</b> — configured default ({@link #defaultTestSection})</li>
     *   <li><b>P5</b> — hardcoded last-resort {@code "Hematology"}</li>
     * </ol>
     *
     * <p>Returns {@code null} only if every priority fails (should not happen on a
     * correctly seeded DB, but is handled gracefully by callers).
     *
     * @param concept the OCL Test concept JSON node
     * @return the resolved {@link TestSection}, or {@code null} if none found
     */
    private TestSection resolveTestSection(JsonNode concept) {
        String conceptId = getText(concept, "id");
        TestSection testSection = null;

        // ── P1: direct ConvSet → Test CONCEPT-SET mapping ────────────────────────
        if (conceptId != null && this.rootNode != null) {
            JsonNode mappings = this.rootNode.get("mappings");
            if (mappings != null && mappings.isArray()) {
                for (JsonNode mapping : mappings) {
                    if (!"CONCEPT-SET".equalsIgnoreCase(getText(mapping, "map_type"))) {
                        continue;
                    }
                    if (!conceptId.equals(getText(mapping, "to_concept_code"))) {
                        continue;
                    }
                    String fromId = getText(mapping, "from_concept_code");
                    if (fromId == null) {
                        continue;
                    }
                    JsonNode fromConcept = getConceptById(fromId);
                    if (fromConcept == null) {
                        continue; // dangling reference (e.g. id=278) — skip gracefully
                    }
                    if (!"ConvSet".equalsIgnoreCase(getText(fromConcept, "concept_class"))) {
                        continue; // LabSet or other — handled in P1.5
                    }
                    String sectionName = getText(fromConcept, "display_name");
                    if (sectionName == null || META_CONVSET_NAMES.contains(sectionName)) {
                        continue; // meta grouping — skip
                    }
                    testSection = testSectionService.getTestSectionByName(sectionName);
                    if (testSection != null) {
                        log.debug("OCL resolveTestSection P1 (direct ConvSet): id='" + conceptId + "' → '" + sectionName + "'");
                        return testSection;
                    }
                    log.warn("OCL resolveTestSection P1: section '" + sectionName + "' not in DB for test '" + conceptId + "' — continuing.");
                }
            }
        }

        // ── P1.5: Test → LabSet → ConvSet chain ──────────────────────────────────
        if (conceptId != null && this.rootNode != null && !labSetToSectionName.isEmpty()) {
            JsonNode mappings = this.rootNode.get("mappings");
            if (mappings != null && mappings.isArray()) {
                for (JsonNode mapping : mappings) {
                    if (!"CONCEPT-SET".equalsIgnoreCase(getText(mapping, "map_type"))) {
                        continue;
                    }
                    if (!conceptId.equals(getText(mapping, "to_concept_code"))) {
                        continue;
                    }
                    String fromId = getText(mapping, "from_concept_code");
                    if (fromId == null) {
                        continue;
                    }
                    String sectionName = labSetToSectionName.get(fromId);
                    if (sectionName == null) {
                        continue; // from side is not a LabSet with a known section
                    }
                    testSection = testSectionService.getTestSectionByName(sectionName);
                    if (testSection != null) {
                        log.debug("OCL resolveTestSection P1.5 (LabSet→ConvSet): id='" + conceptId + "' → '" + sectionName + "'");
                        return testSection;
                    }
                    log.warn("OCL resolveTestSection P1.5: section '" + sectionName + "' not in DB for test '" + conceptId + "' — continuing.");
                }
            }
        }

        // ── P2: OCL extras explicit test_section field ────────────────────────────
        JsonNode extras = concept.get("extras");
        if (extras != null && extras.has("test_section")) {
            String oclTestSection = getText(extras, "test_section");
            testSection = testSectionService.getTestSectionByName(oclTestSection);
            if (testSection != null) {
                log.debug("OCL resolveTestSection P2 (extras): id='" + conceptId + "' → '" + oclTestSection + "'");
                return testSection;
            }
        }

        // ── P3: ocl-test-mapping.json manual override ─────────────────────────────
        String testName = getText(concept, "display_name");
        if (oclMappingService != null && testName != null) {
            OclMappingService.MappingEntry mappingEntry = oclMappingService.getMapping(testName);
            String mappedSection = mappingEntry.getTestSection();
            testSection = testSectionService.getTestSectionByName(mappedSection);
            if (testSection != null) {
                log.debug("OCL resolveTestSection P3 (mapping JSON): '" + testName + "' → '" + mappedSection + "'");
                return testSection;
            }
        }

        // ── P4: configured default ────────────────────────────────────────────────
        testSection = testSectionService.getTestSectionByName(defaultTestSection);
        if (testSection != null) {
            log.debug("OCL resolveTestSection P4 (default): id='" + conceptId + "' → '" + defaultTestSection + "'");
            return testSection;
        }

        // ── P5: hardcoded last-resort ─────────────────────────────────────────────
        testSection = testSectionService.getTestSectionByName("Hematology");
        if (testSection != null) {
            log.debug("OCL resolveTestSection P5 (hardcoded): id='" + conceptId + "' → 'Hematology'");
        }
        return testSection;
    }

    private void mapPanels(JsonNode concept, ObjectNode jsonWad) {
        // Initialize as empty array - can be enhanced to map OCL panel relationships
        ArrayNode panelsArray = objectMapper.createArrayNode();
        jsonWad.set("panels", panelsArray);
    }

    private void mapUnits(JsonNode concept, ObjectNode jsonWad) {
        String units = null;
        String unitsId = null;

        JsonNode extras = concept.get("extras");
        if (extras != null && extras.has("units")) {
            units = getText(extras, "units");
        }

        if (StringUtils.isNotBlank(units)) {
            UnitOfMeasure resolved = resolveOrCreateUom(units);
            if (resolved != null) {
                unitsId = resolved.getId();
            }
        }

        jsonWad.put("uom", unitsId != null ? unitsId : "");
    }

    /**
     * Looks up a UnitOfMeasure by exact name; creates it if it does not exist.
     *
     * <p>Always returns a Hibernate-managed entity (loaded from the session or
     * freshly persisted). Never returns a transient shell — callers can safely
     * assign the result to a persistent entity and call {@code update()} without
     * triggering a {@code TransientPropertyValueException}.
     *
     * <p>Idempotency: exact name match → same row every run.
     * Concurrent-insert safety: if two threads race to insert the same name,
     * the loser catches the duplicate exception and re-fetches.
     *
     * @param units the unit string from OCL extras (e.g. "mg/dL", "10^3/uL")
     * @return the managed {@link UnitOfMeasure}, or {@code null} on failure
     */
    private UnitOfMeasure resolveOrCreateUom(String units) {
        // Step 1: exact name lookup — returns a session-managed entity
        UnitOfMeasure probe = new UnitOfMeasure();
        probe.setUnitOfMeasureName(units);
        UnitOfMeasure dbUom = uomSerivice.getUnitOfMeasureByName(probe);
        if (dbUom != null) {
            return dbUom;
        }

        // Step 2: not found — create it with the exact OCL string as both name and description
        try {
            UnitOfMeasure newUom = new UnitOfMeasure();
            newUom.setUnitOfMeasureName(units);
            newUom.setDescription(units);
            newUom.setSysUserId(systemUserId);
            String newId = uomSerivice.insert(newUom);
            log.info("OCL resolveOrCreateUom: created new UOM '" + units + "' (id=" + newId + ").");
            // Re-fetch the persisted entity so the caller gets a session-managed object
            newUom.setId(newId);
            UnitOfMeasure persisted = uomSerivice.getUnitOfMeasureByName(probe);
            return persisted != null ? persisted : newUom;
        } catch (Exception e) {
            // Concurrent insert by another thread — re-fetch by name
            dbUom = uomSerivice.getUnitOfMeasureByName(probe);
            if (dbUom != null) {
                log.info("OCL resolveOrCreateUom: concurrent insert for '" + units
                        + "' resolved via re-fetch (id=" + dbUom.getId() + ").");
                return dbUom;
            }
            log.error("OCL resolveOrCreateUom: failed to create or find UOM '" + units
                    + "' — test will have no UOM. Error: " + e.getMessage());
            return null;
        }
    }

    private void mapLoinc(String loinc, ObjectNode jsonWad) {
        jsonWad.put("loinc", loinc != null ? loinc : "");
    }

    private void mapGuid(String conceptId, ObjectNode jsonWad) {
        jsonWad.put("guid", conceptId != null ? conceptId : "");
    }

    private String getLoinc(String id) {
        String loinc = null;

        JsonNode mappings = this.rootNode.get("mappings");
        if (mappings != null && mappings.isArray()) {
            String fallbackLoinc = null;

            for (JsonNode mapping : mappings) {
                if (!id.equals(getText(mapping, "from_concept_code"))) {
                    continue;
                }

                String mapType = getText(mapping, "map_type").toUpperCase();
                String toSourceName = getText(mapping, "to_source_name").toUpperCase();
                String candidateLoinc = getText(mapping, "to_concept_code");

                // Priority 1: SAME-AS mapping pointing to LOINC
                if ("SAME-AS".equals(mapType) && "LOINC".equals(toSourceName)
                        && StringUtils.isNotBlank(candidateLoinc)) {
                    loinc = candidateLoinc;
                    log.info("Found SAME-AS LOINC code: " + loinc + " for concept " + id);
                    break; // stop immediately since we found the best match
                }

                // Priority 2: any mapping pointing to LOINC (keep as fallback)
                if ("LOINC".equals(toSourceName) && StringUtils.isNotBlank(candidateLoinc)) {
                    fallbackLoinc = candidateLoinc;
                }
            }

            // If we didn’t find a SAME-AS → LOINC, use fallback if available
            if (loinc == null && fallbackLoinc != null) {
                loinc = fallbackLoinc;
                log.info("Found Other LOINC code: " + loinc + " for concept " + id);
            }
        }
        return loinc;
    }

    private void mapResultType(JsonNode concept, ObjectNode jsonWad) {
        String dataType = getText(concept, "datatype");
        String resultType = "R"; // Default to text

        if (dataType != null) {
            String mappedType = RESULT_TYPE_MAPPING.get(dataType.toUpperCase());
            if (mappedType != null) {
                resultType = mappedType;
            }
        }

        // Convert to actual DB ID if TypeOfTestResultService is available via
        // SpringContext
        try {

            TypeOfTestResult typeObj = typeOfTestResultService.getTypeOfTestResultByType(resultType);
            if (typeObj != null && typeObj.getId() != null) {
                jsonWad.put("resultType", typeObj.getId());
            }
        } catch (Exception e) {
            log.error("Error mapping result type (Spring context not available or service failed): " + e.getMessage(),
                    e);
        }
    }

    private void mapOrderableFlags(JsonNode concept, ObjectNode jsonWad) {

        jsonWad.put("orderable", "Y");
        jsonWad.put("notifyResults", "N");
        jsonWad.put("inLabOnly", "N");
        jsonWad.put("antimicrobialResistance", "N");
        Boolean retired = Boolean.valueOf(getText(concept, "retired"));
        jsonWad.put("active", retired ? "N" : "Y");
    }

    private void mapSampleTypes(JsonNode concept, ObjectNode jsonWad) {
        ArrayNode sampleTypesArray = objectMapper.createArrayNode();

        TypeOfSample typeOfSample = getTypeOfSample(concept);
        if (typeOfSample != null) {
            ObjectNode sampleTypeObj = objectMapper.createObjectNode();
            sampleTypeObj.put("typeId", typeOfSample.getId());

            ArrayNode testsArray = objectMapper.createArrayNode();
            ObjectNode testOrder = objectMapper.createObjectNode();
            testOrder.put("id", 0); // New test placeholder (ID 0 usually means the test being added)
            testsArray.add(testOrder);

            sampleTypeObj.set("tests", testsArray);
            sampleTypesArray.add(sampleTypeObj);
        }

        jsonWad.set("sampleTypes", sampleTypesArray);
    }

    private void mapNumericValidation(JsonNode concept, ObjectNode jsonWad) {
        String dataType = getText(concept, "datatype");
        boolean isNumeric = dataType != null && NUMERIC_DATA_TYPES.contains(dataType.toUpperCase());

        String lowValid = "-Infinity";
        String highValid = "Infinity";
        String lowReporting = "-Infinity";
        String highReporting = "Infinity";
        String lowCritical = "-Infinity";
        String highCritical = "Infinity";
        String lowNormal = "-Infinity";
        String highNormal = "Infinity";
        String sigDigits = "0";

        if (isNumeric) {
            log.info("Mapping NUMERIC result type for concept: " + getText(concept, "id"));

            // Map validation ranges from 'extras' (OCL standard attributes)
            JsonNode extras = concept.get("extras");

            if (extras != null) {
                String lowAbs = getNumericText(extras, "low_absolute");
                if (isNumeric(lowAbs)) {
                    lowValid = lowAbs;
                }

                String hiAbs = getNumericText(extras, "hi_absolute");
                if (isNumeric(hiAbs)) {
                    highValid = hiAbs;
                }
                String lowReportingValue = getNumericText(extras, "low_reporting");
                if (isNumeric(lowReportingValue)) {
                    lowReporting = lowReportingValue;
                }

                String highReportingValue = getNumericText(extras, "hi_reporting");
                if (isNumeric(highReportingValue)) {
                    highReporting = highReportingValue;
                }

                String lowCriticalValue = getNumericText(extras, "low_critical");
                if (isNumeric(lowCriticalValue)) {
                    lowCritical = lowCriticalValue;
                }

                String highCriticalValue = getNumericText(extras, "hi_critical");
                if (isNumeric(highCriticalValue)) {
                    highCritical = highCriticalValue;
                }

                String lowNormalValue = getNumericText(extras, "low_normal");
                if (isNumeric(lowNormalValue)) {
                    lowNormal = lowNormalValue;
                }

                String highNormalValue = getNumericText(extras, "hi_normal");
                if (isNumeric(highNormalValue)) {
                    highNormal = highNormalValue;
                }

                // Check for allow_decimal which indicates significant digits
                String allowDecimal = getNumericText(extras, "allow_decimal");
                if (allowDecimal != null) {
                    if ("true".equalsIgnoreCase(allowDecimal)) {
                        sigDigits = "2"; // Default to 2 decimal places if decimals are allowed
                    } else if ("false".equalsIgnoreCase(allowDecimal)) {
                        sigDigits = "0"; // No decimal places
                    }
                }
            }

        }
        jsonWad.put("lowValid", lowValid);
        jsonWad.put("highValid", highValid);
        jsonWad.put("lowReportingRange", lowReporting);
        jsonWad.put("highReportingRange", highReporting);
        jsonWad.put("lowCritical", lowCritical);
        jsonWad.put("highCritical", highCritical);
        jsonWad.put("lowNormal", lowNormal);
        jsonWad.put("highNormal", highNormal);
        jsonWad.put("significantDigits", sigDigits);
    }

    private static boolean isNumeric(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        try {
            Double.parseDouble(str); // or Integer.parseInt(str) if you want only integers
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void mapResultLimits(JsonNode concept, ObjectNode jsonWad) {
        ArrayNode resultLimitsArray = objectMapper.createArrayNode();
        ObjectNode limit = objectMapper.createObjectNode();
        limit.put("ageRange", "0");
        limit.put("highAgeRange", "Infinity");
        limit.put("gender", false);
        limit.put("lowNormal", jsonWad.get("lowNormal").asText());
        limit.put("highNormal", jsonWad.get("highNormal").asText());
        limit.put("lowNormalFemale", "-Infinity");
        limit.put("highNormalFemale", "Infinity");
        resultLimitsArray.add(limit);
        jsonWad.put("resultLimits", resultLimitsArray);
    }

    private void mapDictionaryResults(JsonNode concept, ObjectNode jsonWad) {
        String dataType = getText(concept, "datatype");
        boolean isDictionary = dataType != null && CODED_DATA_TYPES.contains(dataType.toUpperCase());

        ArrayNode dictionaryArray = objectMapper.createArrayNode();
        if (isDictionary) {
            String conceptId = getText(concept, "id");
            log.info("Mapping CODED/SELECT result type for concept: " + conceptId);

            // Try mappings array first (OCL standard format for Q-AND-A)
            JsonNode mappings = this.rootNode.get("mappings");
            int qAndACount = 0;
            if (mappings != null && mappings.isArray()) {
                for (JsonNode mapping : mappings) {
                    String fromConceptCode = getText(mapping, "from_concept_code");
                    String mapType = getText(mapping, "map_type");
                    if (mapType == null) {
                        continue;
                    }
                    if (!fromConceptCode.equals(conceptId) || !mapType.toUpperCase().equals("Q-AND-A")) {
                        continue;
                    }
                    qAndACount++;
                    String toConceptCode = getText(mapping, "to_concept_code");
                    log.info("  Found Q-AND-A mapping: concept " + conceptId + " -> answer " + toConceptCode);
                    JsonNode mapConcept = getConceptById(toConceptCode);
                    if (mapConcept == null) {
                        log.warn("  Answer concept " + toConceptCode + " not found in concepts array - skipping");
                        continue;
                    }
                    Map<String, String> names = extractNames(mapConcept);
                    String englishName = names.get("englishName");
                    String frenchName = names.get("frenchName");
                    String answerExternalId = getText(mapConcept, "external_id"); // OpenMRS UUID for answer concept
                    log.info("  Creating dictionary entry: " + englishName + " (code: " + toConceptCode
                            + ", external_id: " + answerExternalId + ")");
                    String loinc = getLoinc(toConceptCode);

                    Dictionary dictionary = new Dictionary();
                    dictionary.setSortOrder(1);
                    dictionary.setIsActive("Y");
                    dictionary.setDictEntry(englishName);
                    dictionary.setLocalAbbreviation(toConceptCode);
                    dictionary.setSysUserId(systemUserId);
                    dictionary.setLoincCode(loinc);
                    if (StringUtils.isNotBlank(answerExternalId)) {
                        dictionary.setGuid(answerExternalId);
                    }
                    dictionary.setDictionaryCategory(
                            dictionaryCategoryService.getDictionaryCategoryByName("Test Result"));
                    boolean isDuplicate = dictionaryService.duplicateDictionaryExists(dictionary);
                    log.info("  Dictionary duplicate check for '" + englishName + "': " + isDuplicate);

                    if (isDuplicate) {
                        // Retrieve existing dictionary by name AND category (fixes issue with multiple
                        // dictionaries having same name in different categories)
                        dictionary = dictionaryService.getDictionaryEntrysByNameAndCategoryDescription(englishName,
                                "General test result");
                        log.info("  Retrieved existing dictionary by name+category: "
                                + (dictionary != null ? "id=" + dictionary.getId() : "NULL"));
                        if (dictionary != null) {
                            boolean needsUpdate = false;
                            if (StringUtils.isNotBlank(loinc) && !loinc.equals(dictionary.getLoincCode())) {
                                dictionary.setLoincCode(loinc);
                                needsUpdate = true;
                            }
                            // Update GUID with external_id (OpenMRS UUID) if provided and different
                            if (StringUtils.isNotBlank(answerExternalId)
                                    && !answerExternalId.equals(dictionary.getGuid())) {
                                dictionary.setGuid(answerExternalId);
                                needsUpdate = true;
                                log.info("  Updating GUID for existing dictionary '" + englishName + "' to '"
                                        + answerExternalId + "'");
                            }
                            if (needsUpdate) {
                                dictionary = dictionaryService.update(dictionary);
                                log.info("  Updated existing dictionary: id=" + dictionary.getId());
                            }
                        }
                    } else {
                        Localization localization = createLocalization(frenchName, englishName, "create Dictionary",
                                systemUserId);
                        localization = localizationService.save(localization);
                        dictionary.setLocalizedDictionaryName(localization);
                        dictionary = dictionaryService.save(dictionary);
                        log.info("  Saved new dictionary: "
                                + (dictionary != null ? "id=" + dictionary.getId() : "NULL"));
                    }
                    // Only add to dictionary array if dictionary was successfully created/retrieved
                    if (dictionary != null) {
                        ObjectNode dictEntry = objectMapper.createObjectNode();
                        dictEntry.put("id", String.valueOf(dictionary.getId()));
                        dictEntry.put("qualified", "N");
                        dictionaryArray.add(dictEntry);
                        log.info("  Added to dictionaryArray: id=" + dictionary.getId() + ", array size now: "
                                + dictionaryArray.size());
                    } else {
                        log.warn("  DICTIONARY IS NULL - not added to array!");
                    }

                }
            }
            log.info("  Total Q-AND-A mappings found for concept " + conceptId + ": " + qAndACount
                    + ", dictionary entries created: " + dictionaryArray.size());
            if (qAndACount == 0) {
                log.warn("  No Q-AND-A mappings found for coded concept " + conceptId
                        + " - dictionary array will be empty!");
            }
        }
        jsonWad.put("dictionary", dictionaryArray);
        jsonWad.put("defaultTestResult", "");
        jsonWad.put("dictionaryReference", "");

    }

    public JsonNode getConceptById(String id) {
        JsonNode concepts = this.rootNode.get("concepts");
        if (concepts != null && concepts.isArray()) {
            for (JsonNode conceptNode : concepts) {
                String conceptId = getText(conceptNode, "id");
                if (conceptId.equals(id)) {
                    return conceptNode;
                }

            }
        }
        return null;
    }

    /**
     * Helper to safely extract text from a JsonNode field.
     * 
     * @param node  The JsonNode to extract from.
     * @param field The name of the field.
     * @return The text value, or null if the node or field is missing/null.
     */
    public String getText(JsonNode node, String field) {
        if (node != null && node.has(field)) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private TypeOfSample getTypeOfSample(JsonNode concept) {
        JsonNode extras = concept.get("extras");
        TypeOfSample typeOfSample = null;

        // Priority 1: Check OCL extras for explicit sample_type
        if (extras != null && extras.has("sample_type")) {
            String ocltypeOfSample = getText(extras, "sample_type");
            typeOfSample = typeOfSampleService.getTypeOfSampleByLocalizedName(ocltypeOfSample, Locale.ENGLISH);
            if (typeOfSample != null) {
                log.debug("Using sample_type from OCL extras: " + ocltypeOfSample);
                return typeOfSample;
            }
        }

        // Priority 2: Use OclMappingService lookup based on test name
        String testName = getText(concept, "display_name");
        if (oclMappingService != null && testName != null) {
            OclMappingService.MappingEntry mapping = oclMappingService.getMapping(testName);
            String mappedSampleType = mapping.getSampleType();
            typeOfSample = typeOfSampleService.getTypeOfSampleByLocalizedName(mappedSampleType, Locale.ENGLISH);
            if (typeOfSample != null) {
                log.debug("Using mapped sample_type for test '" + testName + "': " + mappedSampleType);
                return typeOfSample;
            }
        }

        // Priority 3: Fallback to configured default
        if (typeOfSample == null) {
            typeOfSample = typeOfSampleService.getTypeOfSampleByLocalizedName(defaultSampleType, Locale.ENGLISH);
        }

        // Priority 4: Hardcoded fallback
        if (typeOfSample == null) {
            typeOfSample = typeOfSampleService.getTypeOfSampleByLocalizedName("Whole Blood", Locale.ENGLISH);
        }
        return typeOfSample;
    }

    /**
     * Helper method to log detailed concept information for debugging
     */
    private void logConceptDetails(JsonNode concept) {
        try {
            StringBuilder details = new StringBuilder("\nConcept Details:\n");
            details.append("ID: ").append(getText(concept, "id")).append("\n");
            details.append("Display Name: ").append(getText(concept, "display_name")).append("\n");
            details.append("Concept Class: ").append(getText(concept, "concept_class")).append("\n");
            details.append("DataType: ").append(getText(concept, "datatype")).append("\n");

            // Log names
            JsonNode names = concept.get("names");
            if (names != null && names.isArray()) {
                details.append("Names:\n");
                for (JsonNode name : names) {
                    details.append("  - ").append(getText(name, "name")).append(" (").append(getText(name, "locale"))
                            .append(")").append(" [").append(getText(name, "name_type")).append("]\n");
                }
            }

            log.debug(details.toString());
        } catch (Exception e) {
            log.error("Error logging concept details: " + e.getMessage(), e);
        }
    }

    /**
     * Helper to safely extract numeric text from a JsonNode field, handling both
     * number and text nodes.
     * 
     * @param node  The JsonNode to extract from.
     * @param field The name of the field.
     * @return The numeric text value, or null if the node or field is
     *         missing/null/empty.
     */
    private String getNumericText(JsonNode node, String field) {
        if (node != null && node.has(field)) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()
                    && (value.isNumber() || (value.isTextual() && !value.asText().isEmpty()))) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Panel createPanel(String name, String decription, String userId, String loinc, String externalId) {
        Panel panel = new Panel();
        panel.setDescription(decription);
        panel.setPanelName(name);
        panel.setIsActive("N");
        panel.setSortOrderInt(Integer.MAX_VALUE);
        panel.setSysUserId(userId);
        panel.setLoinc(loinc);
        // Set GUID from external_id (OpenMRS UUID) with fallback
        if (StringUtils.isNotBlank(externalId)) {
            panel.setGuid(externalId);
        }
        return panel;
    }

    private Localization createLocalization(String french, String english, String decriptiopn, String currentUserId) {
        Localization localization = new Localization();
        localization.setEnglish(english);
        localization.setFrench(french);
        localization.setDescription(decriptiopn);
        localization.setSysUserId(currentUserId);
        return localization;
    }

    private SystemModule createSystemModule(String menuItem, String identifyingName, String userId) {
        SystemModule module = new SystemModule();
        module.setSystemModuleName(menuItem + ":" + identifyingName);
        module.setDescription(menuItem + "=>panel=>" + identifyingName);
        module.setSysUserId(userId);
        module.setHasAddFlag("Y");
        module.setHasDeleteFlag("Y");
        module.setHasSelectFlag("Y");
        module.setHasUpdateFlag("Y");
        return module;
    }

    private RoleModule createRoleModule(String userId, SystemModule workplanModule, Role role) {
        RoleModule roleModule = new RoleModule();
        roleModule.setRole(role);
        roleModule.setSystemModule(workplanModule);
        roleModule.setSysUserId(userId);
        roleModule.setHasAdd("Y");
        roleModule.setHasDelete("Y");
        roleModule.setHasSelect("Y");
        roleModule.setHasUpdate("Y");
        return roleModule;
    }

    public Set<String> getLabSetMemebrs(JsonNode concept) {
        String dataType = getText(concept, "datatype");
        String conceptClass = getText(concept, "concept_class");
        boolean isLabSet = conceptClass != null && LABSET_CONCEPT_CLASSES.contains(conceptClass.toUpperCase())
                && NONE_DATATYPES.contains(dataType.toUpperCase());

        Set<String> mappedTests = new HashSet<>();
        if (isLabSet) {
            log.info("Mapping LabSet result type for concept: " + getText(concept, "id"));
            String id = getText(concept, "id");

            // Try mappings array first (OCL standard format for LOINC)
            JsonNode mappings = this.rootNode.get("mappings");
            if (mappings != null && mappings.isArray()) {
                for (JsonNode mapping : mappings) {
                    String fromConceptCode = getText(mapping, "from_concept_code");
                    String toConceptCode = getText(mapping, "to_concept_code");
                    String mapType = getText(mapping, "map_type").toUpperCase();
                    if (fromConceptCode.equals(id) && mapType.equals("CONCEPT-SET")) {
                        JsonNode mapConcept = getConceptById(toConceptCode);
                        Map<String, String> names = extractNames(mapConcept);
                        String englishName = names.get("englishName");
                        mappedTests.add(englishName);
                    }
                }
            }
        }
        return mappedTests;
    }
}
