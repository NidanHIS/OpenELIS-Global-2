package org.openelisglobal.dataexchange.externalorders.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.service.IncomingOrderService;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.openelisglobal.nidanpaywall.NidanPaywallClient;
import org.openelisglobal.nidanpaywall.PaywallResult;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.service.PanelItemService;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/incoming-orders")
public class IncomingOrdersRestController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingOrdersRestController.class);

    @Autowired
    private IncomingOrderService incomingOrderService;

    @Autowired
    private PatientService patientService;

    @Autowired
    private PanelItemService panelItemService;

    @Autowired
    private PanelService panelService;

    @Autowired
    private TestService testService;

    @Autowired
    private NidanPaywallClient paywallClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<IncomingOrderListItem> list() {
        List<IncomingOrder> orders = incomingOrderService.getOrders();

        List<IncomingOrderListItem> response = new ArrayList<>();
        for (IncomingOrder order : orders) {
            IncomingOrderListItem item = new IncomingOrderListItem();
            // Existing fields - MUST remain unchanged for collection flow
            item.setExternalOrderNumber(order.getExternalOrderNumber());
            item.setPatientGuid(order.getPatientGuid());
            item.setReceivedTimestamp(order.getReceivedTimestamp());

            // Resolve patient once — extract both name and nationalId in a single lookup
            PatientDisplayInfo patientInfo = resolvePatientDisplayInfo(order.getPatientGuid());
            item.setPatientName(patientInfo.name);
            item.setPatientId(patientInfo.nationalId);

            // Payload-derived display-only fields
            item.setTestCount(calculateTotalTestCount(order.getPayload()));
            item.setSource(extractSource(order.getPayload()));
            item.setVisitType(order.getVisitType());

            response.add(item);
        }
        return response;
    }

    /**
     * Paginated list endpoint for the dashboard left panel.
     *
     * <p>
     * Query parameters (all optional):
     * <ul>
     * <li>{@code page} – 1-based page number, defaults to 1</li>
     * <li>{@code pageSize} – records per page, defaults to 10, max 100</li>
     * <li>{@code dateFrom} – ISO date (yyyy-MM-dd), inclusive lower bound on
     * receivedTimestamp</li>
     * <li>{@code dateTo} – ISO date (yyyy-MM-dd), exclusive upper bound on
     * receivedTimestamp</li>
     * <li>{@code search} – substring match on externalOrderNumber</li>
     * </ul>
     *
     * <p>
     * Response shape:
     * 
     * <pre>
     * {
     *   "items":      [ ...IncomingOrderListItem... ],
     *   "totalCount": 1247,
     *   "page":       1,
     *   "pageSize":   10,
     *   "totalPages": 125
     * }
     * </pre>
     *
     * <p>
     * The existing no-param {@code GET /rest/incoming-orders} is untouched. This
     * endpoint is only activated when at least one query param is present.
     */
    @GetMapping(value = "/paged", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedIncomingOrdersResponse> listPaged(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String search) {

        // Parse optional date bounds
        Timestamp from = parseDateToTimestampStart(dateFrom);
        Timestamp to = parseDateToTimestampStart(dateTo); // exclusive upper bound = start of dateTo day

        IncomingOrderService.PagedIncomingOrders paged = incomingOrderService.getOrdersPage(from, to, search, page,
                pageSize);

        List<IncomingOrderListItem> items = new ArrayList<>();
        for (IncomingOrder order : paged.getItems()) {
            IncomingOrderListItem item = new IncomingOrderListItem();
            item.setExternalOrderNumber(order.getExternalOrderNumber());
            item.setPatientGuid(order.getPatientGuid());
            item.setReceivedTimestamp(order.getReceivedTimestamp());

            PatientDisplayInfo patientInfo = resolvePatientDisplayInfo(order.getPatientGuid());
            item.setPatientName(patientInfo.name);
            item.setPatientId(patientInfo.nationalId);

            item.setTestCount(calculateTotalTestCount(order.getPayload()));
            item.setSource(extractSource(order.getPayload()));
            item.setVisitType(order.getVisitType());

            items.add(item);
        }

        PagedIncomingOrdersResponse response = new PagedIncomingOrdersResponse(items, paged.getTotalCount(),
                paged.getPage(), paged.getPageSize(), paged.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Parses an ISO date string (yyyy-MM-dd) to a Timestamp at the start of that
     * day in the system default timezone. Returns null if the input is null or
     * blank.
     */
    private Timestamp parseDateToTimestampStart(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(isoDate.trim());
            return Timestamp.valueOf(date.atTime(LocalTime.MIDNIGHT));
        } catch (DateTimeParseException e) {
            logger.warn("Invalid date param '{}', ignoring", isoDate);
            return null;
        }
    }

    /** Response envelope for the paginated list endpoint. */
    public static class PagedIncomingOrdersResponse {
        private final List<IncomingOrderListItem> items;
        private final long totalCount;
        private final int page;
        private final int pageSize;
        private final int totalPages;

        public PagedIncomingOrdersResponse(List<IncomingOrderListItem> items, long totalCount, int page, int pageSize,
                int totalPages) {
            this.items = items;
            this.totalCount = totalCount;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages = totalPages;
        }

        public List<IncomingOrderListItem> getItems() {
            return items;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    /**
     * Holds the display-only patient fields resolved from a patientGuid. Both
     * fields are nullable — null means the data was not available.
     */
    private static class PatientDisplayInfo {
        final String name;
        final String nationalId;

        PatientDisplayInfo(String name, String nationalId) {
            this.name = name;
            this.nationalId = nationalId;
        }
    }

    /**
     * Resolve patient display info (name + nationalId) from patientGuid. Fetches
     * the Patient record exactly once. Returns an instance with null fields if the
     * patient cannot be found — never returns null itself.
     */
    private PatientDisplayInfo resolvePatientDisplayInfo(String patientGuid) {
        if (patientGuid == null || patientGuid.trim().isEmpty()) {
            return new PatientDisplayInfo(null, null);
        }
        try {
            Patient patient = patientService.getPatientForGuid(patientGuid);
            if (patient == null) {
                return new PatientDisplayInfo(null, null);
            }

            // Resolve name from Person
            String resolvedName = null;
            if (patient.getPerson() != null) {
                Person person = patient.getPerson();
                String firstName = person.getFirstName();
                String lastName = person.getLastName();
                if (firstName != null && lastName != null) {
                    resolvedName = lastName + ", " + firstName;
                } else if (firstName != null) {
                    resolvedName = firstName;
                } else if (lastName != null) {
                    resolvedName = lastName;
                }
            }

            // SUBJECT (health ID from OpenMRS) is primary; national ID is the fallback.
            String resolvedId = patientService.getSubjectNumber(patient);
            if (resolvedId == null || resolvedId.trim().isEmpty()) {
                resolvedId = patientService.getNationalId(patient);
            }
            if (resolvedId != null && resolvedId.trim().isEmpty()) {
                resolvedId = null;
            }

            return new PatientDisplayInfo(resolvedName, resolvedId);
        } catch (Exception e) {
            logger.debug("Could not resolve patient display info for guid: {}", patientGuid, e);
            return new PatientDisplayInfo(null, null);
        }
    }

    /**
     * Calculate total test count from payload. Includes direct tests + expanded
     * panel tests - removed tests. Uses deduplication to avoid counting same test
     * multiple times.
     */
    private Integer calculateTotalTestCount(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        try {
            ExternalOrderRequest request = objectMapper.readValue(payload, ExternalOrderRequest.class);
            if (request.getSamples() == null || request.getSamples().isEmpty()) {
                return 0;
            }

            Set<String> countedTestIds = new HashSet<>();
            int count = 0;

            for (ExternalOrderRequest.ExternalOrderSample sample : request.getSamples()) {
                // Count direct tests
                if (sample.getTests() != null) {
                    for (ExternalOrderRequest.ExternalOrderTestRef testRef : sample.getTests()) {
                        String testId = resolveTestId(testRef);
                        if (testId != null && countedTestIds.add(testId)) {
                            count++;
                        }
                    }
                }

                // Expand panels and count their tests
                if (sample.getPanels() != null) {
                    for (ExternalOrderRequest.ExternalOrderPanelRef panelRef : sample.getPanels()) {
                        String panelId = resolvePanelId(panelRef);
                        if (panelId != null) {
                            List<PanelItem> panelItems = panelItemService.getPanelItemsForPanel(panelId);
                            if (panelItems != null) {
                                for (PanelItem pi : panelItems) {
                                    if (pi.getTest() != null && pi.getTest().getId() != null) {
                                        String testId = pi.getTest().getId();
                                        if (countedTestIds.add(testId)) {
                                            count++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Subtract removed tests
                if (sample.getRemovedTests() != null) {
                    for (ExternalOrderRequest.ExternalOrderTestRef removedRef : sample.getRemovedTests()) {
                        String testId = resolveTestId(removedRef);
                        if (testId != null && countedTestIds.contains(testId)) {
                            count--;
                        }
                    }
                }
            }

            return count;
        } catch (Exception e) {
            logger.debug("Could not calculate test count from payload", e);
            return null;
        }
    }

    /**
     * True when the source department name indicates an admission visit (IPD / ER).
     * These orders bypass the paywall entirely — admission patients are never
     * blocked. Matches case-insensitively: IPD — "IPD", "ipd", "Inpatient (IPD)",
     * "IPD Ward", etc. ER — "ER", "er", "ER Ward", "Emergency", "emergency room",
     * etc.
     */
    private boolean isAdmissionSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return false;
        }
        String upper = source.trim().toUpperCase();
        return upper.contains("IPD") || upper.contains("INPATIENT") || upper.contains("EMERGENCY")
                || java.util.regex.Pattern.compile("\\bER\\b").matcher(upper).find();
    }

    /**
     * Extract source (referringSiteDepartmentName or referringSiteName) from
     * payload. Prioritizes department name for better granularity.
     */
    private String extractSource(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        try {
            ExternalOrderRequest request = objectMapper.readValue(payload, ExternalOrderRequest.class);
            String departmentName = request.getReferringSiteDepartmentName();
            if (departmentName != null && !departmentName.trim().isEmpty()) {
                return departmentName.trim();
            }

            String referringSiteName = request.getReferringSiteName();
            if (referringSiteName != null && !referringSiteName.trim().isEmpty()) {
                return referringSiteName.trim();
            }
            return null;
        } catch (Exception e) {
            logger.debug("Could not extract source from payload", e);
            return null;
        }
    }

    /**
     * Resolve test ID from test reference (by GUID or LOINC).
     */
    private String resolveTestId(ExternalOrderRequest.ExternalOrderTestRef testRef) {
        if (testRef == null) {
            return null;
        }
        if (testRef.getTestGuid() != null && !testRef.getTestGuid().trim().isEmpty()) {
            Test test = testService.getTestByGUID(testRef.getTestGuid().trim());
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

    /**
     * Resolve panel ID from panel reference (by GUID or LOINC).
     */
    private String resolvePanelId(ExternalOrderRequest.ExternalOrderPanelRef panelRef) {
        if (panelRef == null) {
            return null;
        }
        if (panelRef.getPanelGuid() != null && !panelRef.getPanelGuid().trim().isEmpty()) {
            Panel panel = panelService.getPanelByGUID(panelRef.getPanelGuid().trim());
            return panel != null ? panel.getId() : null;
        }
        if (panelRef.getLoinc() != null && !panelRef.getLoinc().trim().isEmpty()) {
            Panel panel = panelService.getPanelByLoincCode(panelRef.getLoinc().trim());
            return panel != null ? panel.getId() : null;
        }
        return null;
    }

    @GetMapping(value = "/{externalOrderNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("externalOrderNumber") String externalOrderNumber) {
        Optional<IncomingOrder> holdingOpt = incomingOrderService.getOrderByExternalOrderNumber(externalOrderNumber);
        if (holdingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown externalOrderNumber");
        }
        return ResponseEntity.ok(toDetail(holdingOpt.get()));
    }

    @GetMapping(value = "/{externalOrderNumber}/sample-patient-entry-form", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSamplePatientEntryForm(
            @PathVariable("externalOrderNumber") String externalOrderNumber) {
        try {
            SamplePatientEntryForm form = incomingOrderService.buildSamplePatientEntryForm(externalOrderNumber);
            return ResponseEntity.ok(form);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping(value = "/{externalOrderNumber}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("externalOrderNumber") String externalOrderNumber,
            @RequestBody ExternalOrderRequest updatedRequest) {
        try {
            IncomingOrder updated = incomingOrderService.updateOrderByExternalOrderNumber(externalOrderNumber,
                    updatedRequest, null, null);
            return ResponseEntity.ok(toDetail(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping(value = "/{externalOrderNumber}/finalize", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> finalizeHolding(@PathVariable("externalOrderNumber") String externalOrderNumber) {
        try {
            incomingOrderService.finalizeHolding(externalOrderNumber);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Paywall check for the Collect Sample action.
     *
     * <p>
     * Called by the frontend before activating the collect-sample workflow for an
     * incoming order. Returns the Odoo payment decision so the UI can block or warn
     * accordingly.
     *
     * <p>
     * When the paywall is disabled ({@code nidan.paywall.enabled=false}) this
     * always returns {@code decision=allow} without contacting Odoo.
     *
     * @param externalOrderNumber the visit UUID / external order number
     */
    @GetMapping(value = "/{externalOrderNumber}/paywall-check", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> paywallCheck(@PathVariable("externalOrderNumber") String externalOrderNumber) {

        Optional<IncomingOrder> holdingOpt = incomingOrderService.getOrderByExternalOrderNumber(externalOrderNumber);

        if (holdingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Unknown externalOrderNumber");
        }

        IncomingOrder order = holdingOpt.get();

        // IPD / ER bypass: admission orders are never blocked at the paywall.
        // The department name is authoritative (sourced from OpenMRS via middleware).
        String source = extractSource(order.getPayload());
        if (isAdmissionSource(source)) {
            java.util.Map<String, Object> bypass = new java.util.LinkedHashMap<>();
            bypass.put("decision", "allow");
            bypass.put("blocked", false);
            bypass.put("outstandingAmount", 0.0);
            bypass.put("currency", null);
            bypass.put("paymentStatus", "admission_bypass");
            bypass.put("patientGuid", order.getPatientGuid());
            bypass.put("visitUuid", externalOrderNumber);
            return ResponseEntity.ok(bypass);
        }

        PaywallResult result = paywallClient.check(order.getPatientGuid(), externalOrderNumber);

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("decision", result.decision());
        body.put("blocked", result.isBlocked());
        body.put("outstandingAmount", result.outstandingAmount());
        body.put("currency", result.currency());
        body.put("paymentStatus", result.paymentStatus());
        body.put("patientGuid", order.getPatientGuid());
        body.put("visitUuid", externalOrderNumber);
        return ResponseEntity.ok(body);
    }

    private IncomingOrderDetail toDetail(IncomingOrder holding) {
        IncomingOrderDetail detail = new IncomingOrderDetail();
        detail.setExternalOrderNumber(holding.getExternalOrderNumber());
        detail.setPatientGuid(holding.getPatientGuid());
        detail.setPayload(holding.getPayload());
        detail.setReceivedTimestamp(holding.getReceivedTimestamp());
        detail.setReceivedSysUserId(holding.getReceivedSysUserId());
        detail.setCollectedTimestamp(holding.getCollectedTimestamp());
        detail.setCollectedSysUserId(holding.getCollectedSysUserId());
        detail.setLabNo(holding.getLabNo());
        detail.setSampleId(holding.getSampleId());
        detail.setErrorMessage(holding.getErrorMessage());
        detail.setVisitType(holding.getVisitType());
        return detail;
    }

    public static class IncomingOrderDetail {
        private String externalOrderNumber;
        private String patientGuid;
        private String payload;
        private Timestamp receivedTimestamp;
        private String receivedSysUserId;
        private Timestamp collectedTimestamp;
        private String collectedSysUserId;
        private String labNo;
        private Integer sampleId;
        private String errorMessage;
        /** Visit type name sourced from the order event (e.g. "OPD", "IPD"). Nullable. */
        private String visitType;

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

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public Timestamp getReceivedTimestamp() {
            return receivedTimestamp;
        }

        public void setReceivedTimestamp(Timestamp receivedTimestamp) {
            this.receivedTimestamp = receivedTimestamp;
        }

        public String getReceivedSysUserId() {
            return receivedSysUserId;
        }

        public void setReceivedSysUserId(String receivedSysUserId) {
            this.receivedSysUserId = receivedSysUserId;
        }

        public Timestamp getCollectedTimestamp() {
            return collectedTimestamp;
        }

        public void setCollectedTimestamp(Timestamp collectedTimestamp) {
            this.collectedTimestamp = collectedTimestamp;
        }

        public String getCollectedSysUserId() {
            return collectedSysUserId;
        }

        public void setCollectedSysUserId(String collectedSysUserId) {
            this.collectedSysUserId = collectedSysUserId;
        }

        public String getLabNo() {
            return labNo;
        }

        public void setLabNo(String labNo) {
            this.labNo = labNo;
        }

        public Integer getSampleId() {
            return sampleId;
        }

        public void setSampleId(Integer sampleId) {
            this.sampleId = sampleId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getVisitType() {
            return visitType;
        }

        public void setVisitType(String visitType) {
            this.visitType = visitType;
        }
    }

    public static class IncomingOrderListItem {
        // Existing fields - MUST remain for collection flow
        private String externalOrderNumber;
        private String patientGuid;
        private Timestamp receivedTimestamp;

        // Display-only fields - additive, never affect collection flow
        private String patientName;
        private String patientId;
        private Integer testCount;
        private String source;
        /** Visit type name (e.g. "OPD", "IPD"). Display-only, nullable. */
        private String visitType;

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

        public String getPatientName() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public Integer getTestCount() {
            return testCount;
        }

        public void setTestCount(Integer testCount) {
            this.testCount = testCount;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getVisitType() {
            return visitType;
        }

        public void setVisitType(String visitType) {
            this.visitType = visitType;
        }
    }
}
