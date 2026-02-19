package org.openelisglobal.dataexchange.externalorders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.service.IncomingOrderService;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private IncomingOrderService incomingOrderService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private SampleService sampleService;

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

        String externalOrderNumber = externalOrderRequest.getExternalOrderNumber();
        if (externalOrderNumber != null) {
            Sample existingSample = sampleService.getSampleByReferringId(externalOrderNumber);
            if (existingSample != null) {
                ExternalOrderReceivedResponse response = new ExternalOrderReceivedResponse();
                response.setExternalOrderNumber(externalOrderNumber);
                response.setHoldingId(null);
                response.setStatus("COLLECTED");
                response.setAccessionNumber(existingSample.getAccessionNumber());
                response.setMessage("Order has already been collected");
                return ResponseEntity.ok(response);
            }

            Optional<IncomingOrder> existingHolding = incomingOrderService.getOrderByExternalOrderNumber(
                    externalOrderNumber);
            if (existingHolding.isPresent()) {
                Integer holdingId = existingHolding.get().getId();
                ExternalOrderReceivedResponse response = new ExternalOrderReceivedResponse();
                response.setExternalOrderNumber(externalOrderNumber);
                response.setHoldingId(holdingId);
                response.setStatus("ORDER_EXISTS");
                response.setMessage("Order already exists");
                return ResponseEntity.ok(response);
            }
        }

        Integer holdingId;
        try {
            holdingId = incomingOrderService.receiveOrder(externalOrderRequest, payloadJson, null);
        } catch (DataIntegrityViolationException e) {
            if (externalOrderNumber != null) {
                Optional<IncomingOrder> existingHolding = incomingOrderService.getOrderByExternalOrderNumber(
                        externalOrderNumber);
                if (existingHolding.isPresent()) {
                    holdingId = existingHolding.get().getId();
                    ExternalOrderReceivedResponse response = new ExternalOrderReceivedResponse();
                    response.setExternalOrderNumber(externalOrderNumber);
                    response.setHoldingId(holdingId);
                    response.setStatus("ORDER_EXISTS");
                    response.setMessage("Order already exists");
                    return ResponseEntity.ok(response);
                }
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Unable to receive order (possible duplicate externalOrderNumber)");
        }

        ExternalOrderReceivedResponse response = new ExternalOrderReceivedResponse();
        response.setExternalOrderNumber(externalOrderNumber);
        response.setHoldingId(holdingId);
        response.setStatus("CREATED");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateExternalOrder(HttpServletRequest request,
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

        Integer holdingId;
        try {
            holdingId = incomingOrderService
                    .updateOrderByExternalOrderNumber(externalOrderRequest.getExternalOrderNumber(),
                            externalOrderRequest, payloadJson, null)
                    .getId();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Order does not exist or has already been collected");
        }

        ExternalOrderReceivedResponse response = new ExternalOrderReceivedResponse();
        response.setExternalOrderNumber(externalOrderRequest.getExternalOrderNumber());
        response.setHoldingId(holdingId);
        response.setStatus("UPDATED");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping(value = "/{externalOrderNumber}")
    public ResponseEntity<?> deleteExternalOrder(@PathVariable("externalOrderNumber") String externalOrderNumber) {
        try {
            if (incomingOrderService.getOrderByExternalOrderNumber(externalOrderNumber).isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Order doesn't exist.");
            }

            incomingOrderService.deleteHoldingByExternalOrderNumber(externalOrderNumber);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Order can't be deleted after collection");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Minimal response wrapper for the external order endpoint.
     */
    public static class ExternalOrderReceivedResponse {
        private String externalOrderNumber;
        private Integer holdingId;
        private String status;
        private String message;
        private String accessionNumber;

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

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getAccessionNumber() {
            return accessionNumber;
        }

        public void setAccessionNumber(String accessionNumber) {
            this.accessionNumber = accessionNumber;
        }
    }
}
