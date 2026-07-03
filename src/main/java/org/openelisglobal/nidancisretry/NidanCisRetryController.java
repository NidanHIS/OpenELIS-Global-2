package org.openelisglobal.nidancisretry;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /rest/nidan/cis/retry — triggers async CIS dead-letter retry. Returns
 * immediately with {status:"queued", queued:true}. Auth enforced by existing
 * ELIS Spring Security session filter.
 */
@RestController
@RequestMapping("/rest/nidan/cis")
public class NidanCisRetryController {

    @Autowired
    private NidanCisRetryService cisRetryService;

    @PostMapping(value = "/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> retry() {
        cisRetryService.retryAsync();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "queued");
        body.put("queued", true);

        return ResponseEntity.ok(body);
    }
}
