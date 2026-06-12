package org.openelisglobal.nidanpaywall;

import java.util.LinkedHashMap;
import java.util.Map;
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

    @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> check(
            @RequestParam(value = "patientUuid", required = false) String patientUuid,
            @RequestParam(value = "visitUuid", required = false) String visitUuid) {

        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("patientUuid query parameter is required");
        }

        PaywallResult result = paywallClient.check(patientUuid, visitUuid);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", result.decision());
        body.put("blocked", result.isBlocked());
        body.put("outstandingAmount", result.outstandingAmount());
        body.put("currency", result.currency());
        body.put("paymentStatus", result.paymentStatus());
        body.put("patientGuid", patientUuid);
        if (visitUuid != null) {
            body.put("visitUuid", visitUuid);
        }

        return ResponseEntity.ok(body);
    }
}
