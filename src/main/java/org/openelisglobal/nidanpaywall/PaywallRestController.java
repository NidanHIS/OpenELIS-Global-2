package org.openelisglobal.nidanpaywall;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/nidan/paywall")
public class PaywallRestController {

    @Autowired
    private NidanPaywallClient paywallClient;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private NidanPaywallConfigService paywallConfigService;

    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> check(@RequestParam(value = "patientUuid", required = false) String patientUuid,
            @RequestParam(value = "visitUuid", required = false) String visitUuid,
            @RequestParam(value = "labNumber", required = false) String labNumber) {

        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("patientUuid query parameter is required");
        }

        String resolvedVisitUuid = visitUuid;
        Sample sample = null;

        // Resolve visitUuid and sample from labNumber if needed
        if (!GenericValidator.isBlankOrNull(labNumber)) {
            sample = sampleService.getSampleByAccessionNumber(labNumber.trim());
            if (sample != null && GenericValidator.isBlankOrNull(resolvedVisitUuid)
                    && !GenericValidator.isBlankOrNull(sample.getReferringId())) {
                resolvedVisitUuid = sample.getReferringId();
            }
        }

        // Visit-type bypass: mirrors Layer 1 (IncomingOrdersRestController).
        // Reads sample.nidanVisitType persisted at collect time and checks the
        // admin-configured allow-list via NidanPaywallConfigService.
        if (sample != null && paywallConfigService.isVisitTypeAllowed(sample.getNidanVisitType())) {
            Map<String, Object> bypass = new LinkedHashMap<>();
            bypass.put("decision", "allow");
            bypass.put("blocked", false);
            bypass.put("outstandingAmount", 0.0);
            bypass.put("currency", null);
            bypass.put("paymentStatus", "visit_type_bypass");
            bypass.put("visitType", sample.getNidanVisitType());
            bypass.put("patientGuid", patientUuid);
            if (resolvedVisitUuid != null) {
                bypass.put("visitUuid", resolvedVisitUuid);
            }
            return ResponseEntity.ok(bypass);
        }

        PaywallResult result = paywallClient.check(patientUuid, resolvedVisitUuid);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", result.decision());
        body.put("blocked", result.isBlocked());
        body.put("outstandingAmount", result.outstandingAmount());
        body.put("currency", result.currency());
        body.put("paymentStatus", result.paymentStatus());
        body.put("patientGuid", patientUuid);
        if (resolvedVisitUuid != null) {
            body.put("visitUuid", resolvedVisitUuid);
        }

        return ResponseEntity.ok(body);
    }
}
