package org.openelisglobal.dataexchange.externalorders.controller.rest;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.openelisglobal.common.util.StringUtil;
import org.openelisglobal.dataexchange.externalorders.service.ExternalOrderHoldingService;
import org.openelisglobal.dataexchange.externalorders.service.ExternalOrderCollectResult;
import org.openelisglobal.dataexchange.externalorders.valueholder.ExternalOrderHolding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/external-orders-holding")
public class ExternalOrderHoldingRestController {

    @Autowired
    private ExternalOrderHoldingService externalOrderHoldingService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ExternalOrderHoldingListItem> list() {
        List<ExternalOrderHolding> orders = externalOrderHoldingService.getOrders();

        List<ExternalOrderHoldingListItem> response = new ArrayList<>();
        for (ExternalOrderHolding order : orders) {
            ExternalOrderHoldingListItem item = new ExternalOrderHoldingListItem();
            item.setId(order.getId());
            item.setExternalOrderNumber(order.getExternalOrderNumber());
            item.setPatientGuid(order.getPatientGuid());
            item.setReceivedTimestamp(order.getReceivedTimestamp());
            response.add(item);
        }
        return response;
    }

    @PostMapping(value = "/{id}/collect", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> collect(@PathVariable("id") Integer id, HttpServletRequest request) {
        try {
            ExternalOrderCollectResult result = externalOrderHoldingService.collect(id, request);
            if (StringUtil.isNullorNill(result.getExternalOrderNumber())
                    && StringUtil.isNullorNill(request != null ? request.getParameter("externalOrderNumber") : null)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown holdingId");
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    public static class ExternalOrderHoldingListItem {
        private Integer id;
        private String externalOrderNumber;
        private String patientGuid;
        private Timestamp receivedTimestamp;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getExternalOrderNumber() {
            return externalOrderNumber;
        }

        public void setExternalOrderNumber(String externalOrderNumber) {
            this.externalOrderNumber = externalOrderNumber;
        }

        public String getPatientGuid() {
            return patientGuid;
        }

        public void setPatientGuid(String patientGuid) {
            this.patientGuid = patientGuid;
        }

        public Timestamp getReceivedTimestamp() {
            return receivedTimestamp;
        }

        public void setReceivedTimestamp(Timestamp receivedTimestamp) {
            this.receivedTimestamp = receivedTimestamp;
        }
    }
}
