package org.openelisglobal.dataexchange.externalorders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.lang.reflect.InvocationTargetException;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.service.ExternalOrderHoldingService;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
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
    private ExternalOrderHoldingService externalOrderHoldingService;

    @Autowired
    private PatientService patientService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createExternalOrder(HttpServletRequest request,
            @Valid @RequestBody ExternalOrderRequest externalOrderRequest)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        Patient patient = patientService.getPatientForGuid(externalOrderRequest.getPatientGuid());
        if (patient == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unknown patientGuid");
        }

        String payloadJson;
        try {
            payloadJson = new ObjectMapper().writeValueAsString(externalOrderRequest);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid JSON payload");
        }

        Integer holdingId = externalOrderHoldingService.receiveOrder(externalOrderRequest, payloadJson, null);

        ExternalOrderReceivedResponse response = new ExternalOrderReceivedResponse();
        response.setExternalOrderNumber(externalOrderRequest.getExternalOrderNumber());
        response.setHoldingId(holdingId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Minimal response wrapper for the external order endpoint.
     */
    public static class ExternalOrderReceivedResponse {
        private String externalOrderNumber;
        private Integer holdingId;

        public String getExternalOrderNumber() {
            return externalOrderNumber;
        }

        public void setExternalOrderNumber(String externalOrderNumber) {
            this.externalOrderNumber = externalOrderNumber;
        }

        public Integer getHoldingId() {
            return holdingId;
        }

        public void setHoldingId(Integer holdingId) {
            this.holdingId = holdingId;
        }
    }
}

