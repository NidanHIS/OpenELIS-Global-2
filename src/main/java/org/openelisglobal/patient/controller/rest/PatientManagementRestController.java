package org.openelisglobal.patient.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.hibernate.StaleObjectStateException;
import org.openelisglobal.address.service.AddressHierarchyConfigurationHandler;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.dataexchange.fhir.exception.FhirPersistanceException;
import org.openelisglobal.dataexchange.fhir.exception.FhirTransformationException;
import org.openelisglobal.dataexchange.fhir.service.FhirTransformService;
import org.openelisglobal.nidanpatientsync.PatientSavedEvent;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.organization.service.OrganizationTypeService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.organization.valueholder.OrganizationType;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.service.PatientPhotoService;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.util.PatientUtil;
import org.openelisglobal.patient.validator.ValidatePatientInfo;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientidentity.service.PatientIdentityService;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.search.service.SearchResultsService;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/")
public class PatientManagementRestController extends BaseRestController {
    @Autowired
    SearchResultsService searchService;
    @Autowired
    PatientIdentityService patientIdentityService;
    @Autowired
    PatientService patientService;
    @Autowired
    FhirTransformService fhirTransformService;
    @Autowired
    PatientPhotoService photoService;
    @Autowired
    OrganizationService organizationService;
    @Autowired
    OrganizationTypeService organizationTypeService;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    SiteInformationService siteInformationService;

    @PostMapping(value = "PatientManagement", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> savepatient(HttpServletRequest request,
            @Validated(SamplePatientEntryForm.SamplePatientEntry.class) @RequestBody PatientManagementInfo patientInfo,
            BindingResult bindingResult) throws Exception {

        // Read readonly flag once — used for both 403 guard and NID generation guard
        SiteInformation readonlyConfig = siteInformationService.getSiteInformationByName("nidan_patient_ui_readonly");
        boolean isReadonly = readonlyConfig != null && "true".equalsIgnoreCase(readonlyConfig.getValue());

        // Block patient creation/update if readonly flag is enabled
        if (isReadonly) {
            return ResponseEntity.status(403).body(Map.of("status", "FORBIDDEN", "message",
                    "Patient creation/editing is disabled. Use middleware endpoint instead."));
        }

        if (StringUtils.isNotBlank(patientInfo.getPatientPK())) {
            patientInfo.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        } else {
            patientInfo.setPatientUpdateStatus(PatientUpdateStatus.ADD);
        }
        Patient patient = new Patient();

        if (patientInfo.getPatientUpdateStatus() != PatientUpdateStatus.NO_ACTION) {

            // On CREATE: if nationalId is blank AND patient UI is NOT read-only, generate a
            // unique
            // fallback NID (NID-XXXXXXXX). This only runs when users can create patients
            // from the UI.
            // When readonly=true (middleware-only mode), this is skipped — middleware
            // already sends NIDs.
            // On UPDATE: never touch nationalId — preserve whatever is already in DB.
            if (patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD
                    && GenericValidator.isBlankOrNull(patientInfo.getNationalId()) && !isReadonly) {
                patientInfo.setNationalId(generateFallbackNationalId());
            }

            PatientUtil.preparePatientData(bindingResult, request, patientInfo, patient);
            if (bindingResult.hasErrors()) {
                try {
                    throw new BindException(bindingResult);
                } catch (BindException e) {
                    LogEvent.logError(e);
                }
            }

            try {
                patientService.persistPatientData(patientInfo, patient, getSysUserId(request));
                fhirTransformService.transformPersistPatient(patientInfo,
                        (patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD));
                photoService.savePhoto(patient.getId(), patientInfo.getPhoto());
                eventPublisher.publishEvent(new PatientSavedEvent(patientInfo,
                        patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD));
            } catch (LIMSRuntimeException e) {

                if (e.getCause() instanceof StaleObjectStateException) {
                    return ResponseEntity.status(409).body(Map.of("status", "ERROR", "message", "Stale object state"));
                } else {
                    LogEvent.logDebug(e);
                    return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", "Unexpected error"));
                }

            } catch (FhirTransformationException | FhirPersistanceException e) {
                LogEvent.logError(e);
                return ResponseEntity.status(500)
                        .body(Map.of("status", "ERROR", "message", "FHIR transformation/persistence error"));
            }
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "patientId", patient.getId()));
    }

    @PostMapping(value = "CredentialPatientManagement", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveCredentialPatient(HttpServletRequest request,
            @Validated(SamplePatientEntryForm.SamplePatientEntry.class) @RequestBody PatientManagementInfo patientInfo,
            BindingResult bindingResult) throws Exception {

        if (StringUtils.isNotBlank(patientInfo.getPatientPK())) {
            patientInfo.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        } else if (StringUtils.isNotBlank(patientInfo.getGuid())) {
            Patient existingByGuid = patientService.getPatientForGuid(patientInfo.getGuid());
            if (existingByGuid != null) {
                patientInfo.setPatientPK(existingByGuid.getId());
                patientInfo.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
            } else {
                patientInfo.setPatientUpdateStatus(PatientUpdateStatus.ADD);
            }
        } else {
            patientInfo.setPatientUpdateStatus(PatientUpdateStatus.ADD);
        }
        Patient patient = new Patient();

        if (patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.NO_ACTION) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "ERROR", "message", "No action specified for patient"));
        }
        try {
            preparePatientData(bindingResult, request, patientInfo, patient);
            if (bindingResult.hasErrors()) {
                try {
                    throw new BindException(bindingResult);
                } catch (BindException e) {
                    LogEvent.logError(e);
                }
                java.util.Map<String, Object> body = new java.util.HashMap<>();
                java.util.List<java.util.Map<String, String>> errorList = new java.util.ArrayList<>();
                bindingResult.getFieldErrors().forEach(error -> {
                    errorList.add(java.util.Map.of("field", error.getField(), "code",
                            error.getCode() == null ? "" : error.getCode(), "message",
                            error.getDefaultMessage() == null ? "" : error.getDefaultMessage()));
                });
                body.put("status", "ERROR");
                body.put("errors", errorList);
                return ResponseEntity.badRequest().body(body);
            }

            resolveHealthRegionAndDistrict(patientInfo, getSysUserId(request));
            patientService.persistPatientData(patientInfo, patient, getSysUserId(request), true);
            fhirTransformService.transformPersistPatient(patientInfo,
                    (patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD));
            photoService.savePhoto(patient.getId(), patientInfo.getPhoto());
            eventPublisher.publishEvent(new PatientSavedEvent(patientInfo,
                    patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD));
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("status", patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD ? "CREATED" : "UPDATED");
            body.put("patientId", patient.getId());
            body.put("guid", patientInfo.getGuid());
            body.put("fhirUuid", patient.getFhirUuid() != null ? patient.getFhirUuid().toString() : null);
            return ResponseEntity.ok(body);
        } catch (LIMSRuntimeException e) {

            if (e.getCause() instanceof StaleObjectStateException) {
                LogEvent.logDebug(e);
                request.setAttribute(ALLOW_EDITS_KEY, "false");
                return ResponseEntity.status(409)
                        .body(Map.of("status", "ERROR", "message", "Stale object state while saving patient"));
            } else {
                LogEvent.logDebug(e);
                request.setAttribute(ALLOW_EDITS_KEY, "false");
                return ResponseEntity.status(500)
                        .body(Map.of("status", "ERROR", "message", "Unexpected error while saving patient"));
            }

        } catch (FhirTransformationException | FhirPersistanceException e) {
            LogEvent.logError(e);
            return ResponseEntity.status(500)
                    .body(Map.of("status", "ERROR", "message", "FHIR transformation/persistence error"));
        } catch (Exception e) {
            LogEvent.logError(e);
            return ResponseEntity.status(500).body(Map.of("status", "ERROR", "message", "Unexpected server error",
                    "exception", e.getClass().getSimpleName()));
        }
    }

    @GetMapping("patient-photos/{id}/{isThumbnail}")
    public ResponseEntity<Map<String, String>> getPhoto(@PathVariable String id, @PathVariable boolean isThumbnail)
            throws LIMSRuntimeException {
        String photo = photoService.getPhotoByPatientId(id, isThumbnail);
        return ResponseEntity.ok(Map.of("data", photo));
    }

    private void preparePatientData(Errors errors, HttpServletRequest request, PatientManagementInfo patientInfo,
            Patient patient) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        ValidatePatientInfo.validatePatientInfo(errors, patientInfo);
        if (errors.hasErrors()) {
            return;
        }

        initMembers(patient);
        patientInfo.setPatientIdentities(new ArrayList<PatientIdentity>());

        if (patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.UPDATE) {
            Patient dbPatient = loadForUpdate(patientInfo);
            PropertyUtils.copyProperties(patient, dbPatient);
        }

        copyFormBeanToValueHolders(patientInfo, patient);

        setSystemUserID(patientInfo, patient, request);

        setLastUpdatedTimeStamps(patientInfo, patient);
    }

    private void copyFormBeanToValueHolders(PatientManagementInfo patientInfo, Patient patient)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        PropertyUtils.copyProperties(patient, patientInfo);
        PropertyUtils.copyProperties(patient.getPerson(), patientInfo);
    }

    private void setSystemUserID(PatientManagementInfo patientInfo, Patient patient, HttpServletRequest request) {
        patient.setSysUserId(getSysUserId(request));
        patient.getPerson().setSysUserId(getSysUserId(request));

        for (PatientIdentity identity : patientInfo.getPatientIdentities()) {
            identity.setSysUserId(getSysUserId(request));
        }
        if (patientInfo.getPatientContact() != null) {
            patientInfo.getPatientContact().setSysUserId(getSysUserId(request));
        }
    }

    private void initMembers(Patient patient) {
        patient.setPerson(new Person());
    }

    private void setLastUpdatedTimeStamps(PatientManagementInfo patientInfo, Patient patient) {
        String patientUpdate = patientInfo.getPatientLastUpdated();
        if (!org.apache.commons.validator.GenericValidator.isBlankOrNull(patientUpdate)) {
            Timestamp timeStamp = Timestamp.valueOf(patientUpdate);
            patient.setLastupdated(timeStamp);
        }

        String personUpdate = patientInfo.getPersonLastUpdated();
        if (!org.apache.commons.validator.GenericValidator.isBlankOrNull(personUpdate)) {
            Timestamp timeStamp = Timestamp.valueOf(personUpdate);
            patient.getPerson().setLastupdated(timeStamp);
        }
    }

    private Patient loadForUpdate(PatientManagementInfo patientInfo) {
        Patient patient = patientService.get(patientInfo.getPatientPK());
        patientInfo.setPatientIdentities(patientIdentityService.getPatientIdentitiesForPatient(patient.getId()));
        return patient;
    }

    /**
     * Resolves healthRegion and healthDistrict name strings (sent by external
     * systems) to internal Organization IDs before persisting.
     *
     * Type resolution order per field: 1. Use the OrganizationType that has
     * hierarchy_level = 1 (region) or 2 (district). 2. Fall back to legacy "Health
     * Region" / "Health District" type names if no hierarchy-level type exists in
     * this installation.
     *
     * This ensures the resolved org ID matches exactly what the UI dropdown holds,
     * because the dropdown (health-regions endpoint) uses the same type-resolution
     * logic.
     *
     * Only called from saveCredentialPatient — never touches the UI form path.
     */
    @Transactional
    private void resolveHealthRegionAndDistrict(PatientManagementInfo patientInfo, String sysUserId) {
        String regionTypeName = getOrgTypeNameForHierarchyLevel(1, "Health Region");
        String resolvedRegionId = resolveOrgNameToId(patientInfo.getHealthRegion(), regionTypeName, null, sysUserId);
        if (resolvedRegionId != null) {
            patientInfo.setHealthRegion(resolvedRegionId);
        }

        String districtTypeName = getOrgTypeNameForHierarchyLevel(2, "Health District");
        // Pass resolved region ID as parent so newly created districts are linked
        // to their province. If region was blank/unresolved, parentOrgId is null
        // → district is created without a parent (safe, just won't cascade in
        // dropdown).
        String resolvedDistrictId = resolveOrgNameToId(patientInfo.getHealthDistrict(), districtTypeName,
                resolvedRegionId, sysUserId);
        if (resolvedDistrictId != null) {
            patientInfo.setHealthDistrict(resolvedDistrictId);
        }
    }

    /**
     * Returns the OrganizationType name for the given hierarchy level. Iterates all
     * org types and returns the first one whose hierarchy_level matches. Falls back
     * to the provided fallbackTypeName if none found (null-safe).
     */
    private String getOrgTypeNameForHierarchyLevel(int level, String fallbackTypeName) {
        try {
            List<OrganizationType> allTypes = organizationTypeService.getAllOrganizationTypes();
            if (allTypes != null) {
                for (OrganizationType orgType : allTypes) {
                    if (AddressHierarchyConfigurationHandler.getHierarchyLevel(orgType) == level) {
                        return orgType.getName();
                    }
                }
            }
        } catch (Exception e) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "getOrgTypeNameForHierarchyLevel",
                    "Could not resolve hierarchy level " + level + ", falling back to: " + fallbackTypeName);
        }
        return fallbackTypeName;
    }

    /**
     * Returns the Organization ID for the given name+typeName. Looks up
     * case-insensitively; creates new org if not found. Returns null if
     * incomingValue is blank (nothing to do).
     *
     * @param parentOrgId optional — if provided and a new org is created, sets this
     *                    as the parent (used to link districts to provinces).
     *                    Null-safe: if null or org not found, parent is not set.
     */
    @Transactional
    private String resolveOrgNameToId(String incomingValue, String orgTypeName, String parentOrgId, String sysUserId) {
        if (GenericValidator.isBlankOrNull(incomingValue)) {
            return null;
        }

        // Fetch all active orgs of this type
        List<Organization> orgs = organizationService.getOrganizationsByTypeName("organizationName", orgTypeName);

        // Case-insensitive name match
        String trimmedIncoming = incomingValue.trim().toLowerCase();
        for (Organization org : orgs) {
            if (org.getOrganizationName() != null
                    && org.getOrganizationName().trim().toLowerCase().equals(trimmedIncoming)) {
                return org.getId();
            }
        }

        // No match — create new org of this type
        OrganizationType orgType = organizationTypeService.getOrganizationTypeByName(orgTypeName);
        if (orgType == null) {
            // Type doesn't exist in this installation — store raw string as-is
            LogEvent.logWarn(this.getClass().getSimpleName(), "resolveOrgNameToId",
                    "OrganizationType not found: " + orgTypeName + " — storing raw value: " + incomingValue);
            return null;
        }

        Organization newOrg = new Organization();
        newOrg.setOrganizationName(incomingValue.trim());
        newOrg.setShortName(
                incomingValue.trim().length() > 15 ? incomingValue.trim().substring(0, 15) : incomingValue.trim());
        newOrg.setIsActive(IActionConstants.YES);
        newOrg.setMlsSentinelLabFlag("N");
        newOrg.setSysUserId(sysUserId);

        // Set parent org if provided — links district to its province
        if (!GenericValidator.isBlankOrNull(parentOrgId)) {
            try {
                Organization parentOrg = organizationService.getOrganizationById(parentOrgId);
                if (parentOrg != null) {
                    newOrg.setOrganization(parentOrg);
                }
            } catch (Exception e) {
                // Parent lookup failed — create org without parent, no crash
                LogEvent.logWarn(this.getClass().getSimpleName(), "resolveOrgNameToId",
                        "Could not load parent org id=" + parentOrgId + ", creating without parent");
            }
        }

        String newId = organizationService.insert(newOrg);
        organizationService.linkOrganizationAndType(newOrg, orgType.getId());
        DisplayListService.getInstance().refreshList(ListType.PATIENT_HEALTH_REGIONS);

        LogEvent.logInfo(this.getClass().getSimpleName(), "resolveOrgNameToId",
                "Created new Organization '" + incomingValue.trim() + "' (id=" + newId + ") for type: " + orgTypeName);

        return newId;
    }

    /**
     * Generates a unique fallback National ID for patients who do not provide one.
     * Format: {@code NID-XXXXXXXX} where X is an uppercase alphanumeric character
     * (8 chars). Example: {@code NID-A3F7K2P9}
     *
     * <p>
     * Only called on patient CREATE when nationalId is blank. Never called on
     * UPDATE — existing DB value is preserved.
     */
    private static String generateFallbackNationalId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1 to avoid visual confusion
        java.util.Random rng = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("NID-");
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
