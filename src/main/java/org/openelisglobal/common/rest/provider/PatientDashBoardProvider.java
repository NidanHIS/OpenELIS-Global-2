package org.openelisglobal.common.rest.provider;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.rest.provider.bean.homedashboard.AverageTimeDisplayBean;
import org.openelisglobal.common.rest.provider.bean.homedashboard.DashBoardMetrics;
import org.openelisglobal.common.rest.provider.bean.homedashboard.DashBoardTile;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;
import org.openelisglobal.common.rest.provider.form.PatientDashBoardForm;
import org.openelisglobal.common.rest.util.PatientDashBoardPaging;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.ExternalOrderStatus;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.dataexchange.fhir.FhirConfig;
import org.openelisglobal.dataexchange.fhir.FhirUtil;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.dataexchange.service.order.ElectronicOrderService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/")
public class PatientDashBoardProvider {

    @Autowired
    AnalysisService analysisService;

    @Autowired
    IStatusService iStatusService;

    @Autowired
    ElectronicOrderService electronicOrderService;

    @Autowired
    SampleHumanService sampleHumanService;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private FhirUtil fhirUtil;

    @Autowired
    private FhirConfig fhirConfig;

    @Autowired
    private TestService testService;

    @Autowired
    SystemUserService systemUserService;

    private double calculateAverageReceptionToValidationTime() {
        List<Analysis> analyses = analysisService.getAnalysesCompletedOnByStatusId(DateUtil.getNowAsSqlDate(),
                iStatusService.getStatusID(AnalysisStatus.Finalized));

        List<Long> hours = new ArrayList<>();
        analyses.forEach(analysis -> {
            // Convert java.sql.Date to java.time.LocalDate
            LocalDate localStartDate = analysis.getStartedDate().toLocalDate();
            LocalDate localEndDate = analysis.getReleasedDate().toLocalDate();
            // Calculate time difference in hours
            Long hoursDiff = Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours();
            hours.add(hoursDiff);
        });

        long sum = 0;
        if (!hours.isEmpty()) {
            for (Long h : hours) {
                sum += h;
            }
        }
        return hours.isEmpty() ? 0.0 : (double) sum / hours.size();
    }

    private double calculateAverageReceptionToResultTime() {
        Set<Integer> statusIdSet = new HashSet<>();
        statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
        List<Analysis> analyses = analysisService
                .getAnalysesResultEnteredOnExcludedByStatusId(DateUtil.getNowAsSqlDate(), statusIdSet);

        List<Long> hours = new ArrayList<>();
        analyses.forEach(analysis -> {
            // Convert java.sql.Date to java.time.LocalDate
            LocalDate localStartDate = analysis.getStartedDate().toLocalDate();
            LocalDate localEndDate = analysis.getCompletedDate().toLocalDate();
            // Calculate time difference in hours
            Long hoursDiff = Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours();
            hours.add(hoursDiff);
        });

        long sum = 0;
        if (!hours.isEmpty()) {
            for (Long h : hours) {
                sum += h;
            }
        }
        return hours.isEmpty() ? 0.0 : (double) sum / hours.size();
    }

    private double calculateAverageResultToValidationTime() {
        List<Analysis> analyses = analysisService.getAnalysesCompletedOnByStatusId(DateUtil.getNowAsSqlDate(),
                iStatusService.getStatusID(AnalysisStatus.Finalized));

        List<Long> hours = new ArrayList<>();
        analyses.forEach(analysis -> {
            // Convert java.sql.Date to java.time.LocalDate
            LocalDate localStartDate = analysis.getCompletedDate().toLocalDate();
            LocalDate localEndDate = analysis.getReleasedDate().toLocalDate();
            // Calculate time difference in hours
            Long hoursDiff = Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours();
            hours.add(hoursDiff);
        });

        long sum = 0;
        if (!hours.isEmpty()) {
            for (Long h : hours) {
                sum += h;
            }
        }
        return hours.isEmpty() ? 0.0 : (double) sum / hours.size();
    }

    private List<Analysis> analysesWithDelayedTurnAroundTime() {
        List<Analysis> analyses = analysisService.getAnalysesCompletedOnByStatusId(DateUtil.getNowAsSqlDate(),
                iStatusService.getStatusID(AnalysisStatus.Finalized));

        List<Analysis> delayedAnalyses = new ArrayList<>();
        analyses.forEach(analysis -> {
            // Convert java.sql.Date to java.time.LocalDate
            LocalDate localStartDate = analysis.getStartedDate().toLocalDate();
            LocalDate localEndDate = analysis.getReleasedDate().toLocalDate();
            // Calculate time difference in hours
            Long hoursDiff = Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours();
            if (hoursDiff > 96) {
                delayedAnalyses.add(analysis);
            }
        });
        return delayedAnalyses;
    }

    private List<Analysis> unprintedResults() {
        List<Analysis> analyses = analysisService.getAnalysesCompletedOnByStatusId(DateUtil.getNowAsSqlDate(),
                iStatusService.getStatusID(AnalysisStatus.Finalized));

        List<Analysis> unprintedAnalyses = new ArrayList<>();
        if (analyses == null) {
            return unprintedAnalyses;
        }
        analyses.forEach(a -> {
            if (!analysisService.patientReportHasBeenDone(a)) {
                unprintedAnalyses.add(a);
            }
        });
        return unprintedAnalyses;
    }

    private List<OrderDisplayBean> convertAnalysesToOrderBean(List<Analysis> analyses) {
        List<OrderDisplayBean> orderBeanList = new ArrayList<>();
        if (analyses != null) {
            analyses.forEach(analysis -> {
                if (analysis != null) {
                    OrderDisplayBean orderBean = new OrderDisplayBean();
                    orderBean.setId(analysis.getId());
                    Sample sample = analysis.getSampleItem() != null ? analysis.getSampleItem().getSample() : null;
                    if (sample != null) {
                        Patient patient = sampleHumanService.getPatientForSample(sample);
                        orderBean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                        orderBean.setLabNumber(sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "");
                        orderBean.setPatientId(
                                patient != null ? StringUtils.defaultString(patient.getNationalId()) : "");
                        orderBean.setPatientName(getPatientName(patient));
                    }
                    orderBean.setOrderDate(analysis.getStartedDateForDisplay());
                    orderBean.setTestName(analysis.getTest() != null ? analysis.getTest().getLocalizedName() : "");
                    orderBean
                            .setTestSection(analysis.getTestSection() != null ? analysis.getTestSection().getId() : "");
                    orderBeanList.add(orderBean);
                }
            });
        }

        return orderBeanList;
    }

    private List<OrderDisplayBean> convertAnalysesToGroupedOrderBean(List<Analysis> pendingResultAnalyses,
            List<Analysis> pendingValidationAnalyses) {
        Map<String, OrderDisplayBean> groupedOrders = new LinkedHashMap<>();

        addAnalysesToGroupedOrders(groupedOrders, pendingResultAnalyses, true);
        addAnalysesToGroupedOrders(groupedOrders, pendingValidationAnalyses, false);

        groupedOrders.values().forEach(orderBean -> orderBean
                .setTestCount(orderBean.getPendingResultCount() + orderBean.getPendingValidationCount()));

        return new ArrayList<>(groupedOrders.values());
    }

    private void addAnalysesToGroupedOrders(Map<String, OrderDisplayBean> groupedOrders, List<Analysis> analyses,
            boolean pendingResult) {
        if (analyses == null) {
            return;
        }

        analyses.forEach(analysis -> {
            if (analysis == null) {
                return;
            }

            Sample sample = analysis.getSampleItem() != null ? analysis.getSampleItem().getSample() : null;
            String labNumber = sample != null ? sample.getAccessionNumber() : null;
            String key = labNumber != null ? labNumber : analysis.getId();

            OrderDisplayBean orderBean = groupedOrders.computeIfAbsent(key, ignoredKey -> {
                OrderDisplayBean bean = new OrderDisplayBean();
                bean.setId(analysis.getId());

                if (sample != null) {
                    Patient patient = sampleHumanService.getPatientForSample(sample);
                    bean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                    bean.setLabNumber(sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "");
                    bean.setPatientId(patient != null ? StringUtils.defaultString(patient.getNationalId()) : "");
                    bean.setPatientName(getPatientName(patient));
                }

                bean.setOrderDate(analysis.getStartedDateForDisplay());
                bean.setTestSection(analysis.getTestSection() != null ? analysis.getTestSection().getId() : "");
                return bean;
            });

            if (pendingResult) {
                orderBean.setPendingResultCount(orderBean.getPendingResultCount() + 1);
            } else {
                orderBean.setPendingValidationCount(orderBean.getPendingValidationCount() + 1);
            }
        });
    }

    private String getPatientName(Patient patient) {
        if (patient == null || patient.getPerson() == null) {
            return "";
        }

        List<String> patientNameParts = new ArrayList<>();
        if (StringUtils.isNotBlank(patient.getPerson().getLastName())) {
            patientNameParts.add(patient.getPerson().getLastName().trim());
        }
        if (StringUtils.isNotBlank(patient.getPerson().getFirstName())) {
            patientNameParts.add(patient.getPerson().getFirstName().trim());
        }

        return String.join(", ", patientNameParts);
    }

    private List<OrderDisplayBean> convertAnalysesToUserOrdersBean(List<Analysis> analyses) {
        List<OrderDisplayBean> userOrders = new ArrayList<>();
        Map<String, List<Analysis>> userOrdersMap = new HashMap<>();
        analyses.forEach(analysis -> {
            String systemUserId = analysis.getSampleItem().getSample().getSysUserId();
            if (userOrdersMap.containsKey(systemUserId)) {
                userOrdersMap.get(systemUserId).add(analysis);
            } else {
                List<Analysis> userAnalyses = new ArrayList<>();
                userAnalyses.add(analysis);
                userOrdersMap.put(systemUserId, userAnalyses);
            }
        });

        userOrdersMap.forEach((userId, analysisList) -> {
            OrderDisplayBean userOrderBean = new OrderDisplayBean();
            SystemUser user = systemUserService.get(userId);
            if (user != null) {
                userOrderBean.setId(userId);
                userOrderBean.setUserFirstName(user.getFirstName());
                userOrderBean.setUserLastName(user.getLastName());
                userOrderBean.setCountOfOrdersEntered(userOrdersMap.get(userId).size());
                userOrders.add(userOrderBean);
            }
        });
        return userOrders;
    }

    private List<OrderDisplayBean> getUserOrderBeans(List<Analysis> analyses, String userId) {
        Map<String, List<Analysis>> userOrdersMap = new HashMap<>();
        analyses.forEach(analysis -> {
            String systemUserId = analysis.getSampleItem().getSample().getSysUserId();
            if (userOrdersMap.containsKey(systemUserId)) {
                userOrdersMap.get(systemUserId).add(analysis);
            } else {
                List<Analysis> userAnalyses = new ArrayList<>();
                userAnalyses.add(analysis);
                userOrdersMap.put(systemUserId, userAnalyses);
            }
        });

        if (userOrdersMap.get(userId) != null) {
            return convertAnalysesToOrderBean(userOrdersMap.get(userId));
        }
        return new ArrayList<>();
    }

    private List<OrderDisplayBean> convertElectronicToOrderBean(List<ElectronicOrder> eOrders) {
        List<OrderDisplayBean> orderBeanList = new ArrayList<>();
        eOrders.forEach(eOrder -> {
            OrderDisplayBean orderBean = new OrderDisplayBean();
            orderBean.setId(eOrder.getId());
            orderBean.setPriority(eOrder.getPriority().toString());
            orderBean.setOrderDate(DateUtil.convertTimestampToStringDate(eOrder.getOrderTimestamp()));
            Sample sample = sampleService.getSampleByReferringId(eOrder.getExternalId());
            if (sample != null) {
                orderBean.setLabNumber(sample.getAccessionNumber());
            }

            Test test = null;
            try {
                IGenericClient fhirClient = fhirUtil.getFhirClient(fhirConfig.getLocalFhirStorePath());
                ServiceRequest serviceRequest = fhirClient.read().resource(ServiceRequest.class)
                        .withId(eOrder.getExternalId()).execute();
                for (Coding coding : serviceRequest.getCode().getCoding()) {
                    if (coding.hasSystem()) {
                        if (coding.getSystem().equalsIgnoreCase("http://loinc.org")) {
                            List<Test> tests = testService.getActiveTestsByLoinc(coding.getCode());
                            if (tests.size() != 0) {
                                test = tests.get(0);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
            if (test != null) {
                orderBean.setTestName(test.getLocalizedTestName().getLocalizedValue());
            }

            orderBean.setPatientId(eOrder.getPatient().getNationalId());
            orderBeanList.add(orderBean);
        });

        return orderBeanList;
    }

    @GetMapping(value = "home-dashboard/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DashBoardMetrics getDasBoardTiles() {

        DashBoardMetrics metrics = new DashBoardMetrics();
        java.sql.Timestamp startTimestamp = DateUtil
                .convertStringDateStringTimeToTimestamp(DateUtil.getCurrentDateAsText(), "00:00:00.0");
        java.sql.Timestamp endTimestamp = DateUtil
                .convertStringDateStringTimeToTimestamp(DateUtil.getCurrentDateAsText(), "23:59:59");
        DashBoardTile.TileType.stream().forEach(type -> {
            List<Integer> statusIdList;
            Set<Integer> statusIdSet;
            switch (type) {
            case ORDERS_IN_PROGRESS:
                statusIdList = new ArrayList<>();
                statusIdList.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.NotStarted)));
                metrics.setOrdersInProgress(analysisService.getCountOfAnalysesForStatusIds(statusIdList));
                break;
            case ORDERS_READY_FOR_VALIDATION:
                statusIdList = new ArrayList<>();
                statusIdList.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance)));
                metrics.setOrdersReadyForValidation(analysisService.getCountOfAnalysesForStatusIds(statusIdList));
                break;
            case ORDERS_COMPLETED_TODAY:
                statusIdList = new ArrayList<>();
                statusIdList.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.Finalized)));
                metrics.setOrdersCompletedToday(analysisService
                        .getCountOfAnalysisCompletedOnByStatusId(DateUtil.getNowAsSqlDate(), statusIdList));
                break;
            case ORDERS_PATIALLY_COMPLETED_TODAY:
                statusIdSet = new HashSet<>();
                statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
                statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.Finalized)));
                metrics.setPatiallyCompletedToday(analysisService
                        .getCountOfAnalysisStartedOnExcludedByStatusId(DateUtil.getNowAsSqlDate(), statusIdSet));
                break;

            case ORDERS_ENTERED_BY_USER_TODAY:
                statusIdSet = new HashSet<>();
                statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
                metrics.setOrderEnterdByUserToday(analysisService
                        .getCountOfAnalysisStartedOnExcludedByStatusId(DateUtil.getNowAsSqlDate(), statusIdSet));
                break;
            case ORDERS_REJECTED_TODAY:
                statusIdList = new ArrayList<>();
                statusIdList.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
                metrics.setOrdersRejectedToday(analysisService
                        .getCountOfAnalysisStartedOnByStatusId(DateUtil.getNowAsSqlDate(), statusIdList));
                break;
            case UN_PRINTED_RESULTS:
                metrics.setUnPritendResults(unprintedResults().size());
                break;
            case INCOMING_ORDERS:
                List<Integer> estausIds = new ArrayList<>();
                estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.Entered)));
                estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.NonConforming)));
                metrics.setIncomigOrders(electronicOrderService.getCountOfElectronicOrdersByStatusList(estausIds));
                break;
            case AVERAGE_TURN_AROUND_TIME:
                metrics.setAverageTurnAroudTime(calculateAverageReceptionToValidationTime());
                break;
            case DELAYED_TURN_AROUND:
                metrics.setDelayedTurnAround(analysesWithDelayedTurnAroundTime().size());
                break;
            default:
                break;
            }
        });

        return metrics;
    }

    /**
     * Get the list of orders to be displayed on the dashboard. It will returna a
     * list of orders based on the type of the list in paginated manner.
     */
    @GetMapping(value = "home-dashboard/{listType}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientDashBoardForm getDashBoardDisplayList(HttpServletRequest request,
            @PathVariable DashBoardTile.TileType listType, @RequestParam(required = false) String systemUserId)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        PatientDashBoardForm response = new PatientDashBoardForm();
        PatientDashBoardPaging paging = new PatientDashBoardPaging();
        List<OrderDisplayBean> orderDisplayBeans = new ArrayList<>();

        String requestedPage = request.getParameter("page");
        if (GenericValidator.isBlankOrNull(requestedPage)) {
            orderDisplayBeans = retreiveOrders(listType, systemUserId);

            // All the orders retreived are fed into paging to return the first page of the
            // list.
            paging.setDatabaseResults(request, response, orderDisplayBeans);
        } else {
            int requestedPageNumber = Integer.parseInt(requestedPage);

            // Sets the requested page in the response.
            paging.page(request, response, requestedPageNumber);
        }

        return response;
    }

    @GetMapping(value = "home-dashboard/ORDERS-Grouped", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientDashBoardForm getGroupedOrders(HttpServletRequest request)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        PatientDashBoardForm response = new PatientDashBoardForm();
        PatientDashBoardPaging paging = new PatientDashBoardPaging();
        List<OrderDisplayBean> orderDisplayBeans = new ArrayList<>();

        String requestedPage = request.getParameter("page");
        if (GenericValidator.isBlankOrNull(requestedPage)) {
            List<Analysis> pendingResultAnalyses = analysisService
                    .getAnalysesForStatusId(iStatusService.getStatusID(AnalysisStatus.NotStarted));
            List<Analysis> pendingValidationAnalyses = analysisService
                    .getAnalysesForStatusId(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));
            orderDisplayBeans = convertAnalysesToGroupedOrderBean(pendingResultAnalyses, pendingValidationAnalyses);

            paging.setDatabaseResults(request, response, orderDisplayBeans);
        } else {
            int requestedPageNumber = Integer.parseInt(requestedPage);

            paging.page(request, response, requestedPageNumber);
        }

        return response;
    }

    @GetMapping(value = "home-dashboard/VALIDATION-Grouped", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientDashBoardForm getGroupedValidationOrders(HttpServletRequest request)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        PatientDashBoardForm response = new PatientDashBoardForm();
        PatientDashBoardPaging paging = new PatientDashBoardPaging();
        List<OrderDisplayBean> orderDisplayBeans = new ArrayList<>();

        String requestedPage = request.getParameter("page");
        if (GenericValidator.isBlankOrNull(requestedPage)) {
            List<Analysis> pendingValidationAnalyses = analysisService
                    .getAnalysesForStatusId(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));
            orderDisplayBeans = convertAnalysesToGroupedOrderBean(new ArrayList<>(), pendingValidationAnalyses);

            paging.setDatabaseResults(request, response, orderDisplayBeans);
        } else {
            int requestedPageNumber = Integer.parseInt(requestedPage);

            paging.page(request, response, requestedPageNumber);
        }

        return response;
    }

    /**
     * Returns all grouped orders regardless of status (NotStarted,
     * TechnicalAcceptance, Finalized) so the dashboard can persist completed
     * records. Finalized orders are marked with completed=true so the frontend can
     * render a "Completed" badge.
     */
    @GetMapping(value = "home-dashboard/ORDERS-All-Grouped", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientDashBoardForm getAllGroupedOrders(HttpServletRequest request)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        PatientDashBoardForm response = new PatientDashBoardForm();
        PatientDashBoardPaging paging = new PatientDashBoardPaging();
        List<OrderDisplayBean> orderDisplayBeans = new ArrayList<>();

        String requestedPage = request.getParameter("page");
        if (GenericValidator.isBlankOrNull(requestedPage)) {
            List<String> activeStatusIds = new ArrayList<>();
            activeStatusIds.add(iStatusService.getStatusID(AnalysisStatus.NotStarted));
            activeStatusIds.add(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));

            List<String> finalizedStatusIds = new ArrayList<>();
            finalizedStatusIds.add(iStatusService.getStatusID(AnalysisStatus.Finalized));

            List<Analysis> activeAnalyses = analysisService.getAnalysesForStatusIds(activeStatusIds);
            List<Analysis> finalizedAnalyses = analysisService.getAnalysesForStatusIds(finalizedStatusIds);

            // Build grouped beans for active orders (pending result + pending validation)
            List<Analysis> pendingResult = new ArrayList<>();
            List<Analysis> pendingValidation = new ArrayList<>();
            if (activeAnalyses != null) {
                for (Analysis a : activeAnalyses) {
                    String statusId = a.getStatusId();
                    if (iStatusService.getStatusID(AnalysisStatus.NotStarted).equals(statusId)) {
                        pendingResult.add(a);
                    } else {
                        pendingValidation.add(a);
                    }
                }
            }
            orderDisplayBeans = convertAnalysesToGroupedOrderBean(pendingResult, pendingValidation);

            // Collect accession numbers already represented by active beans so we
            // can skip adding a duplicate finalized bean for mixed-state orders
            // (e.g. 24 finalized + 4 still pending in the same accession).
            Set<String> activeAccessions = new HashSet<>();
            for (OrderDisplayBean b : orderDisplayBeans) {
                if (b.getLabNumber() != null && !b.getLabNumber().trim().isEmpty()) {
                    activeAccessions.add(b.getLabNumber().trim());
                }
            }

            // Add finalized orders — only for accessions NOT already in the active list.
            if (finalizedAnalyses != null) {
                Map<String, OrderDisplayBean> finalizedMap = new LinkedHashMap<>();
                for (Analysis analysis : finalizedAnalyses) {
                    if (analysis == null)
                        continue;
                    org.openelisglobal.sample.valueholder.Sample sample = analysis.getSampleItem() != null
                            ? analysis.getSampleItem().getSample()
                            : null;
                    String labNumber = sample != null ? sample.getAccessionNumber() : null;
                    String key = labNumber != null ? labNumber : analysis.getId();

                    // Skip: this accession already has an active bean — no duplicate row needed
                    if (activeAccessions.contains(key)) {
                        continue;
                    }

                    finalizedMap.computeIfAbsent(key, k -> {
                        OrderDisplayBean bean = new OrderDisplayBean();
                        bean.setId(analysis.getId());
                        if (sample != null) {
                            org.openelisglobal.patient.valueholder.Patient patient = sampleHumanService
                                    .getPatientForSample(sample);
                            bean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                            bean.setLabNumber(sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "");
                            bean.setPatientId(
                                    patient != null ? StringUtils.defaultString(patient.getNationalId()) : "");
                            bean.setPatientName(getPatientName(patient));
                        }
                        bean.setOrderDate(analysis.getStartedDateForDisplay());
                        bean.setTestSection(analysis.getTestSection() != null ? analysis.getTestSection().getId() : "");
                        bean.setCompleted(true);
                        return bean;
                    });
                }
                orderDisplayBeans.addAll(finalizedMap.values());
            }

            // Build a map of accessionNumber -> sampleId from all analyses so we can
            // query the true total count per sample without N+1 calls.
            // We combine active + finalized to cover every accession in the result set.
            Map<String, String> accessionToSampleId = new LinkedHashMap<>();
            List<Analysis> allAnalyses = new ArrayList<>();
            if (activeAnalyses != null)
                allAnalyses.addAll(activeAnalyses);
            if (finalizedAnalyses != null)
                allAnalyses.addAll(finalizedAnalyses);
            for (Analysis a : allAnalyses) {
                if (a == null)
                    continue;
                org.openelisglobal.sample.valueholder.Sample s = a.getSampleItem() != null
                        ? a.getSampleItem().getSample()
                        : null;
                if (s != null && s.getAccessionNumber() != null && s.getId() != null) {
                    accessionToSampleId.putIfAbsent(s.getAccessionNumber().trim(), s.getId());
                }
            }

            // For each unique sample, get the real total count of ALL analyses
            // (any status: NotStarted, TechnicalAcceptance, Finalized, Rejected, etc.)
            // This is the one true number that never changes when a result is reverted.
            Map<String, Integer> accessionToTotalCount = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : accessionToSampleId.entrySet()) {
                List<Analysis> allForSample = analysisService.getAnalysesBySampleId(entry.getValue());
                accessionToTotalCount.put(entry.getKey(), allForSample != null ? allForSample.size() : 0);
            }

            // Apply the true total count to every bean
            for (OrderDisplayBean bean : orderDisplayBeans) {
                String key = bean.getLabNumber() != null ? bean.getLabNumber().trim() : "";
                Integer total = accessionToTotalCount.get(key);
                if (total != null) {
                    bean.setTestCount(total);
                }
            }

            paging.setDatabaseResults(request, response, orderDisplayBeans);
        } else {
            int requestedPageNumber = Integer.parseInt(requestedPage);
            paging.page(request, response, requestedPageNumber);
        }

        return response;
    }

    /**
     * Returns the list of orders based on the type of the list provided by the
     * getdashBoardDisplayList method.
     */
    private List<OrderDisplayBean> retreiveOrders(DashBoardTile.TileType listType, String systemUserId) {
        Set<Integer> statusIdSet;
        List<Analysis> analyses;
        java.sql.Timestamp startTimestamp = DateUtil
                .convertStringDateStringTimeToTimestamp(DateUtil.getCurrentDateAsText(), "00:00:00.0");
        java.sql.Timestamp endTimestamp = DateUtil
                .convertStringDateStringTimeToTimestamp(DateUtil.getCurrentDateAsText(), "23:59:59");
        switch (listType) {
        case ORDERS_IN_PROGRESS:
            analyses = analysisService.getAnalysesForStatusId(iStatusService.getStatusID(AnalysisStatus.NotStarted));
            return convertAnalysesToOrderBean(analyses);
        case ORDERS_READY_FOR_VALIDATION:
            analyses = analysisService
                    .getAnalysesForStatusId(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));
            return convertAnalysesToOrderBean(analyses);
        case ORDERS_COMPLETED_TODAY:
            analyses = analysisService.getAnalysesCompletedOnByStatusId(DateUtil.getNowAsSqlDate(),
                    iStatusService.getStatusID(AnalysisStatus.Finalized));
            return convertAnalysesToOrderBean(analyses);
        case ORDERS_PATIALLY_COMPLETED_TODAY:
            statusIdSet = new HashSet<>();
            statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
            statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.Finalized)));
            analyses = analysisService.getAnalysisStartedOnExcludedByStatusId(DateUtil.getNowAsSqlDate(), statusIdSet);
            return convertAnalysesToOrderBean(analyses);
        case ORDERS_ENTERED_BY_USER_TODAY:
            statusIdSet = new HashSet<>();
            statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
            analyses = analysisService.getAnalysisStartedOnExcludedByStatusId(DateUtil.getNowAsSqlDate(), statusIdSet);
            return convertAnalysesToUserOrdersBean(analyses);
        case ORDERS_REJECTED_TODAY:
            analyses = analysisService.getAnalysisStartedOnRangeByStatusId(DateUtil.getNowAsSqlDate(),
                    DateUtil.getNowAsSqlDate(), iStatusService.getStatusID(AnalysisStatus.SampleRejected));
            return convertAnalysesToOrderBean(analyses);
        case UN_PRINTED_RESULTS:
            return convertAnalysesToOrderBean(unprintedResults());
        case INCOMING_ORDERS:
            List<Integer> estausIds = new ArrayList<>();
            estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.Entered)));
            estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.NonConforming)));
            List<ElectronicOrder> eOrders = electronicOrderService.getAllElectronicOrdersByStatusList(estausIds,
                    ElectronicOrder.SortOrder.STATUS_ID);
            return convertElectronicToOrderBean(eOrders);
        case AVERAGE_TURN_AROUND_TIME:
            return new ArrayList<>();
        case DELAYED_TURN_AROUND:
            return convertAnalysesToOrderBean(analysesWithDelayedTurnAroundTime());
        case ORDERS_FOR_USER:
            if (StringUtils.isNotBlank(systemUserId)) {
                statusIdSet = new HashSet<>();
                statusIdSet.add(Integer.parseInt(iStatusService.getStatusID(AnalysisStatus.SampleRejected)));
                analyses = analysisService.getAnalysisStartedOnExcludedByStatusId(DateUtil.getNowAsSqlDate(),
                        statusIdSet);
                return getUserOrderBeans(analyses, systemUserId);
            }
        }
        return new ArrayList<>();
    }

    @GetMapping(value = "home-dashboard/turn-around-time-metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AverageTimeDisplayBean getDasBoardAverageTurnAroundTime() {
        AverageTimeDisplayBean timeBean = new AverageTimeDisplayBean();
        timeBean.setReceptionToResult(calculateAverageReceptionToResultTime());
        timeBean.setReceptionToValidation(calculateAverageReceptionToValidationTime());
        timeBean.setResultToValidation(calculateAverageResultToValidationTime());
        return timeBean;
    }

    /**
     * Paginated endpoint for the dashboard "Samples Collected / On Going Orders"
     * right panel.
     *
     * <p>
     * Returns a page of grouped order beans (one row per accession number) covering
     * NotStarted, TechnicalAcceptance, and Finalized analyses — the same logical
     * dataset as {@code ORDERS-All-Grouped} but with true DB-level pagination
     * instead of session-based fake pagination.
     *
     * <p>
     * The old {@code ORDERS-All-Grouped} endpoint is completely untouched.
     *
     * <p>
     * Query parameters (all optional):
     * <ul>
     * <li>{@code page} – 1-based page number, defaults to 1</li>
     * <li>{@code pageSize} – records per page, defaults to 10, max 100</li>
     * </ul>
     *
     * <p>
     * Response shape:
     * 
     * <pre>
     * {
     *   "items":      [ ...OrderDisplayBean... ],
     *   "totalCount": 4821,
     *   "page":       1,
     *   "pageSize":   10,
     *   "totalPages": 483
     * }
     * </pre>
     */
    @GetMapping(value = "home-dashboard/grouped-orders/paged", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<PagedGroupedOrdersResponse> getGroupedOrdersPaged(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search) {

        // Collect all three status IDs that belong in this view:
        // NotStarted (pending result), TechnicalAcceptance (pending validation),
        // Finalized (completed). Same set as getAllGroupedOrders.
        List<String> allStatusIds = new ArrayList<>();
        allStatusIds.add(iStatusService.getStatusID(AnalysisStatus.NotStarted));
        allStatusIds.add(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));
        allStatusIds.add(iStatusService.getStatusID(AnalysisStatus.Finalized));

        // Get the page of distinct sample IDs from the service (2 DB queries total).
        AnalysisService.PagedSampleIds pagedIds = analysisService.getPagedSampleIdsForStatuses(allStatusIds, page,
                pageSize, search);

        List<String> sampleIds = pagedIds.getSampleIds();
        List<OrderDisplayBean> items = new ArrayList<>();

        if (!sampleIds.isEmpty()) {
            // Scope the full-status fetches to only the sample IDs on this page.
            // We fetch all analyses for the relevant statuses and then filter by
            // the page's sample IDs — this keeps us within proven HQL patterns
            // and avoids introducing a new batch-by-sample-id query.
            Set<String> sampleIdSet = new HashSet<>(sampleIds);

            List<String> activeStatusIds = new ArrayList<>();
            activeStatusIds.add(iStatusService.getStatusID(AnalysisStatus.NotStarted));
            activeStatusIds.add(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));

            List<String> finalizedStatusIds = new ArrayList<>();
            finalizedStatusIds.add(iStatusService.getStatusID(AnalysisStatus.Finalized));

            List<Analysis> activeAnalyses = analysisService.getAnalysesForStatusIds(activeStatusIds);
            List<Analysis> finalizedAnalyses = analysisService.getAnalysesForStatusIds(finalizedStatusIds);

            // Filter to only the sample IDs on this page
            List<Analysis> pageActiveAnalyses = filterAnalysesBySampleIds(activeAnalyses, sampleIdSet);
            List<Analysis> pageFinalizedAnalyses = filterAnalysesBySampleIds(finalizedAnalyses, sampleIdSet);

            // Split active into pendingResult / pendingValidation
            List<Analysis> pendingResult = new ArrayList<>();
            List<Analysis> pendingValidation = new ArrayList<>();
            for (Analysis a : pageActiveAnalyses) {
                if (iStatusService.getStatusID(AnalysisStatus.NotStarted).equals(a.getStatusId())) {
                    pendingResult.add(a);
                } else {
                    pendingValidation.add(a);
                }
            }

            // Build grouped beans for active orders (reuses existing private method)
            items = convertAnalysesToGroupedOrderBean(pendingResult, pendingValidation);

            // Track active accessions to avoid duplicate rows for mixed-state orders
            Set<String> activeAccessions = new HashSet<>();
            for (OrderDisplayBean b : items) {
                if (b.getLabNumber() != null && !b.getLabNumber().trim().isEmpty()) {
                    activeAccessions.add(b.getLabNumber().trim());
                }
            }

            // Add finalized-only accessions (same dedup logic as getAllGroupedOrders)
            if (!pageFinalizedAnalyses.isEmpty()) {
                Map<String, OrderDisplayBean> finalizedMap = new LinkedHashMap<>();
                for (Analysis analysis : pageFinalizedAnalyses) {
                    if (analysis == null)
                        continue;
                    Sample sample = analysis.getSampleItem() != null ? analysis.getSampleItem().getSample() : null;
                    String labNumber = sample != null ? sample.getAccessionNumber() : null;
                    String key = labNumber != null ? labNumber : analysis.getId();

                    if (activeAccessions.contains(key))
                        continue;

                    finalizedMap.computeIfAbsent(key, k -> {
                        OrderDisplayBean bean = new OrderDisplayBean();
                        bean.setId(analysis.getId());
                        if (sample != null) {
                            Patient patient = sampleHumanService.getPatientForSample(sample);
                            bean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                            bean.setLabNumber(sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "");
                            bean.setPatientId(
                                    patient != null ? StringUtils.defaultString(patient.getNationalId()) : "");
                            bean.setPatientName(getPatientName(patient));
                        }
                        bean.setOrderDate(analysis.getStartedDateForDisplay());
                        bean.setTestSection(analysis.getTestSection() != null ? analysis.getTestSection().getId() : "");
                        bean.setCompleted(true);
                        return bean;
                    });
                }
                items.addAll(finalizedMap.values());
            }

            // Apply true total test count per sample (same logic as getAllGroupedOrders)
            // Build accessionNumber -> sampleId map from page analyses
            Map<String, String> accessionToSampleId = new LinkedHashMap<>();
            List<Analysis> allPageAnalyses = new ArrayList<>();
            allPageAnalyses.addAll(pageActiveAnalyses);
            allPageAnalyses.addAll(pageFinalizedAnalyses);
            for (Analysis a : allPageAnalyses) {
                if (a == null)
                    continue;
                Sample s = a.getSampleItem() != null ? a.getSampleItem().getSample() : null;
                if (s != null && s.getAccessionNumber() != null && s.getId() != null) {
                    accessionToSampleId.putIfAbsent(s.getAccessionNumber().trim(), s.getId());
                }
            }
            // One query per unique sample on this page (bounded by pageSize, max 100)
            Map<String, Integer> accessionToTotalCount = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : accessionToSampleId.entrySet()) {
                List<Analysis> allForSample = analysisService.getAnalysesBySampleId(entry.getValue());
                accessionToTotalCount.put(entry.getKey(), allForSample != null ? allForSample.size() : 0);
            }
            for (OrderDisplayBean bean : items) {
                String key = bean.getLabNumber() != null ? bean.getLabNumber().trim() : "";
                Integer total = accessionToTotalCount.get(key);
                if (total != null) {
                    bean.setTestCount(total);
                }
            }
        }

        PagedGroupedOrdersResponse response = new PagedGroupedOrdersResponse(items, pagedIds.getTotalCount(),
                pagedIds.getPage(), pagedIds.getPageSize(), pagedIds.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Paginated endpoint for the "Orders Ready for Validation" dashboard tile.
     *
     * <p>
     * Returns only {@link AnalysisStatus#TechnicalAcceptance} analyses, grouped by
     * accession number — the same set as the legacy {@code VALIDATION-Grouped}
     * session endpoint, but served page-by-page without loading everything into the
     * HTTP session.
     *
     * <p>
     * Query parameters (all optional):
     * <ul>
     * <li>{@code page} – 1-based page number, defaults to 1</li>
     * <li>{@code pageSize} – records per page, defaults to 10, max 100</li>
     * </ul>
     *
     * <p>
     * Response shape is identical to {@code grouped-orders/paged}.
     */
    @GetMapping(value = "home-dashboard/validation-orders/paged", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<PagedGroupedOrdersResponse> getValidationOrdersPaged(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false) String search) {

        // Only TechnicalAcceptance — same as the legacy VALIDATION-Grouped endpoint.
        List<String> statusIds = new ArrayList<>();
        statusIds.add(iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance));

        // Page of distinct sample IDs (2 DB queries: count + page).
        AnalysisService.PagedSampleIds pagedIds = analysisService.getPagedSampleIdsForStatuses(statusIds, page,
                pageSize, search);

        List<String> sampleIds = pagedIds.getSampleIds();
        List<OrderDisplayBean> items = new ArrayList<>();

        if (!sampleIds.isEmpty()) {
            Set<String> sampleIdSet = new HashSet<>(sampleIds);

            // Fetch all TechnicalAcceptance analyses, then scope to this page's samples.
            List<Analysis> allValidationAnalyses = analysisService.getAnalysesForStatusIds(statusIds);
            List<Analysis> pageAnalyses = filterAnalysesBySampleIds(allValidationAnalyses, sampleIdSet);

            // All analyses on this page are pending-validation — no pendingResult split
            // needed.
            List<Analysis> pendingResult = new ArrayList<>();
            items = convertAnalysesToGroupedOrderBean(pendingResult, pageAnalyses);

            // Apply true total test count per sample (bounded by pageSize, max 100).
            Map<String, String> accessionToSampleId = new LinkedHashMap<>();
            for (Analysis a : pageAnalyses) {
                if (a == null)
                    continue;
                Sample s = a.getSampleItem() != null ? a.getSampleItem().getSample() : null;
                if (s != null && s.getAccessionNumber() != null && s.getId() != null) {
                    accessionToSampleId.putIfAbsent(s.getAccessionNumber().trim(), s.getId());
                }
            }
            Map<String, Integer> accessionToTotalCount = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : accessionToSampleId.entrySet()) {
                List<Analysis> allForSample = analysisService.getAnalysesBySampleId(entry.getValue());
                accessionToTotalCount.put(entry.getKey(), allForSample != null ? allForSample.size() : 0);
            }
            for (OrderDisplayBean bean : items) {
                String key = bean.getLabNumber() != null ? bean.getLabNumber().trim() : "";
                Integer total = accessionToTotalCount.get(key);
                if (total != null) {
                    bean.setTestCount(total);
                }
            }
        }

        PagedGroupedOrdersResponse response = new PagedGroupedOrdersResponse(items, pagedIds.getTotalCount(),
                pagedIds.getPage(), pagedIds.getPageSize(), pagedIds.getTotalPages());

        return ResponseEntity.ok(response);
    }

    /**
     * Filters a list of analyses to only those whose sample ID is in the given set.
     * Used by {@link #getGroupedOrdersPaged} to scope the full-status-fetch down to
     * the current page's sample IDs.
     */
    private List<Analysis> filterAnalysesBySampleIds(List<Analysis> analyses, Set<String> sampleIdSet) {
        if (analyses == null || sampleIdSet == null || sampleIdSet.isEmpty()) {
            return new ArrayList<>();
        }
        List<Analysis> filtered = new ArrayList<>();
        for (Analysis a : analyses) {
            if (a == null)
                continue;
            Sample s = a.getSampleItem() != null ? a.getSampleItem().getSample() : null;
            if (s != null && s.getId() != null && sampleIdSet.contains(s.getId())) {
                filtered.add(a);
            }
        }
        return filtered;
    }

    /** Response envelope for the paginated grouped-orders endpoint. */
    public static class PagedGroupedOrdersResponse {
        private final List<OrderDisplayBean> items;
        private final long totalCount;
        private final int page;
        private final int pageSize;
        private final int totalPages;

        public PagedGroupedOrdersResponse(List<OrderDisplayBean> items, long totalCount, int page, int pageSize,
                int totalPages) {
            this.items = items;
            this.totalCount = totalCount;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages = totalPages;
        }

        public List<OrderDisplayBean> getItems() {
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
}
