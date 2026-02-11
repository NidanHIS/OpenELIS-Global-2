package org.openelisglobal.dataexchange.externalorders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.service.PanelItemService;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.util.AccessionNumberUtil;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.openelisglobal.sample.service.SamplePatientEntryOrderPlacementService;
import org.openelisglobal.sample.validator.SamplePatientEntryFormValidator;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * New, minimal external-order endpoint that reuses the existing
 * SamplePatientEntry pipeline.
 *
 * This controller:
 * - Accepts a focused ExternalOrderRequest JSON payload
 * - Maps it into SamplePatientEntryForm
 * - Delegates to SamplePatientEntryRestController.samplePatientEntrySave(...)
 *   so that orders are created exactly as if they came from the UI.
 */
@Controller
@RequestMapping(value = "/rest/external-orders")
public class ExternalOrderRestController {

    @Autowired
    private SamplePatientEntryFormValidator formValidator;

    @Autowired
    private SamplePatientEntryOrderPlacementService samplePatientEntryOrderPlacementService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private TestService testService;

    @Autowired
    private PanelService panelService;

    @Autowired
    private PanelItemService panelItemService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createExternalOrder(HttpServletRequest request,
            @Valid @RequestBody ExternalOrderRequest externalOrderRequest)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        SamplePatientEntryForm form = new SamplePatientEntryForm();
        form.setSampleOrderItems(new SampleOrderItem());

        Patient patient = patientService.getPatientForGuid(externalOrderRequest.getPatientGuid());
        if (patient == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unknown patientGuid");
        }

        PatientManagementInfo patientInfo = new PatientManagementInfo();
        patientInfo.setPatientPK(patient.getId());
        patientInfo.setPatientUpdateStatus(PatientUpdateStatus.NO_ACTION);
        form.setPatientProperties(patientInfo);
        form.setPatientUpdateStatus(PatientUpdateStatus.NO_ACTION);

        form.getSampleOrderItems().setExternalOrderNumber(externalOrderRequest.getExternalOrderNumber());
        form.getSampleOrderItems().setReferringSiteId(externalOrderRequest.getReferringSiteId());
        form.getSampleOrderItems().setProviderPersonId(externalOrderRequest.getProviderPersonId());

        if (externalOrderRequest.getPriority() != null) {
            form.getSampleOrderItems().setPriority(OrderPriority.valueOf(externalOrderRequest.getPriority()));
        } else {
            form.getSampleOrderItems().setPriority(OrderPriority.ROUTINE);
        }

        if (externalOrderRequest.getProgramId() != null) {
            form.getSampleOrderItems().setProgramId(externalOrderRequest.getProgramId());
        }

        if (externalOrderRequest.getReceivedDate() != null) {
            form.getSampleOrderItems().setReceivedDateForDisplay(toUiDate(externalOrderRequest.getReceivedDate()));
        }
        if (externalOrderRequest.getReceivedTime() != null) {
            form.getSampleOrderItems().setReceivedTime(externalOrderRequest.getReceivedTime());
        }
        if (externalOrderRequest.getRequestDate() != null) {
            form.getSampleOrderItems().setRequestDate(toUiDate(externalOrderRequest.getRequestDate()));
        }

        String fallbackUiDate = pickFallbackCollectionDate(externalOrderRequest);
        if (form.getSampleOrderItems().getReceivedDateForDisplay() == null
                || form.getSampleOrderItems().getReceivedDateForDisplay().trim().isEmpty()) {
            form.getSampleOrderItems().setReceivedDateForDisplay(fallbackUiDate);
        }
        if (form.getSampleOrderItems().getReceivedTime() == null
                || form.getSampleOrderItems().getReceivedTime().trim().isEmpty()) {
            form.getSampleOrderItems().setReceivedTime("00:00");
        }
        if (form.getSampleOrderItems().getRequestDate() == null
                || form.getSampleOrderItems().getRequestDate().trim().isEmpty()) {
            form.getSampleOrderItems().setRequestDate(fallbackUiDate);
        }

        if (form.getSampleOrderItems().getLabNo() == null || form.getSampleOrderItems().getLabNo().trim().isEmpty()) {
            form.getSampleOrderItems().setLabNo(generateAccessionNumber());
        }

        ExternalOrderXmlBuilder xmlBuilder = new ExternalOrderXmlBuilder();
        List<ExternalOrderRequest.ExternalOrderSample> samples = externalOrderRequest.getSamples();

        String fallbackCollectionDate = fallbackUiDate;
        for (ExternalOrderRequest.ExternalOrderSample sample : samples) {
            if (sample.getCollectionDate() == null || sample.getCollectionDate().trim().isEmpty()) {
                sample.setCollectionDate(fallbackCollectionDate);
            }
            if (sample.getCollectionTime() == null || sample.getCollectionTime().trim().isEmpty()) {
                sample.setCollectionTime("00:00");
            }
        }

        List<List<String>> sampleTestIds = new ArrayList<>();
        List<List<String>> samplePanelIds = new ArrayList<>();

        for (ExternalOrderRequest.ExternalOrderSample sample : samples) {
            Set<String> testIds = new LinkedHashSet<>();
            Set<String> panelIds = new LinkedHashSet<>();

            if (sample.getTests() != null) {
                for (ExternalOrderRequest.ExternalOrderTestRef testRef : sample.getTests()) {
                    String id = resolveTestId(testRef);
                    if (id == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unknown test reference");
                    }
                    testIds.add(id);
                }
            }

            if (sample.getPanels() != null) {
                for (ExternalOrderRequest.ExternalOrderPanelRef panelRef : sample.getPanels()) {
                    Panel panel = resolvePanel(panelRef);
                    if (panel == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unknown panel reference");
                    }
                    panelIds.add(panel.getId());

                    List<PanelItem> panelItems = panelItemService.getPanelItemsForPanel(panel.getId());
                    if (panelItems != null) {
                        for (PanelItem pi : panelItems) {
                            if (pi.getTest() != null && pi.getTest().getId() != null) {
                                testIds.add(pi.getTest().getId());
                            }
                        }
                    }
                }
            }

            sampleTestIds.add(new ArrayList<>(testIds));
            samplePanelIds.add(new ArrayList<>(panelIds));
        }

        form.setSampleXML(xmlBuilder.buildSamplesXml(samples, sampleTestIds, samplePanelIds));

        BindingResult bindingResult = new BeanPropertyBindingResult(form, "samplePatientEntryForm");
        formValidator.validate(form, bindingResult);
        samplePatientEntryOrderPlacementService.placeOrder(request, form, bindingResult);

        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(bindingResult.getAllErrors());
        }

        ExternalOrderResponse response = new ExternalOrderResponse();
        response.setExternalOrderNumber(form.getSampleOrderItems().getExternalOrderNumber());
        response.setLabNo(form.getSampleOrderItems().getLabNo());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String resolveTestId(ExternalOrderRequest.ExternalOrderTestRef testRef) {
        if (testRef == null) {
            return null;
        }
        if (testRef.getTestGuid() != null && !testRef.getTestGuid().trim().isEmpty()) {
            Test test = testService.getTestByGUID(testRef.getTestGuid());
            return test != null ? test.getId() : null;
        }
        if (testRef.getLoinc() != null && !testRef.getLoinc().trim().isEmpty()) {
            List<Test> tests = testService.getActiveTestsByLoinc(testRef.getLoinc().trim());
            if (tests != null && !tests.isEmpty()) {
                return tests.get(0).getId();
            }
        }
        return null;
    }

    private Panel resolvePanel(ExternalOrderRequest.ExternalOrderPanelRef panelRef) {
        if (panelRef == null) {
            return null;
        }
        if (panelRef.getPanelGuid() != null && !panelRef.getPanelGuid().trim().isEmpty()) {
            return panelService.getPanelByGUID(panelRef.getPanelGuid());
        }
        if (panelRef.getLoinc() != null && !panelRef.getLoinc().trim().isEmpty()) {
            return panelService.getPanelByLoincCode(panelRef.getLoinc().trim());
        }
        return null;
    }

    private String generateAccessionNumber() {
        int attempts = 0;
        while (attempts < 100) {
            String candidate = AccessionNumberUtil.getMainAccessionNumberGenerator().getNextAvailableAccessionNumber(
                    null, true);
            if (candidate != null && !AccessionNumberUtil.isUsed(candidate)) {
                return candidate;
            }
            attempts++;
        }
        throw new IllegalStateException("Unable to generate accession number");
    }

    private String toUiDate(String date) {
        if (date == null) {
            return null;
        }
        String d = date.trim();
        if (d.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] parts = d.split("-");
            return parts[1] + "/" + parts[2] + "/" + parts[0];
        }
        return d;
    }

    private String pickFallbackCollectionDate(ExternalOrderRequest request) {
        if (request.getReceivedDate() != null && !request.getReceivedDate().trim().isEmpty()) {
            return toUiDate(request.getReceivedDate());
        }
        if (request.getRequestDate() != null && !request.getRequestDate().trim().isEmpty()) {
            return toUiDate(request.getRequestDate());
        }
        return toUiDate(LocalDate.now().toString());
    }

    /**
     * Minimal response wrapper for the external order endpoint.
     */
    public static class ExternalOrderResponse {
        private String externalOrderNumber;
        private String labNo;

        public String getExternalOrderNumber() {
            return externalOrderNumber;
        }

        public void setExternalOrderNumber(String externalOrderNumber) {
            this.externalOrderNumber = externalOrderNumber;
        }

        public String getLabNo() {
            return labNo;
        }

        public void setLabNo(String labNo) {
            this.labNo = labNo;
        }
    }
}

