package org.openelisglobal.dataexchange.externalorders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.dataexchange.externalorders.dao.IncomingOrderDAO;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.service.SamplePatientEntryOrderPlacementService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

@Service
public class IncomingOrderServiceImpl extends AuditableBaseObjectServiceImpl<IncomingOrder, Integer>
        implements IncomingOrderService {

    @Autowired
    protected IncomingOrderDAO baseObjectDAO;

    @Autowired
    private ExternalOrderFormMapperService externalOrderFormMapperService;

    @Autowired
    private SamplePatientEntryOrderPlacementService samplePatientEntryOrderPlacementService;

    @Autowired
    private SampleService sampleService;

    IncomingOrderServiceImpl() {
        super(IncomingOrder.class);
        this.auditTrailLog = false;
    }

    @Override
    protected IncomingOrderDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }

    @Override
    @Transactional
    public Integer receiveOrder(ExternalOrderRequest externalOrderRequest, String payloadJson, String receivedSysUserId) {
        IncomingOrder holding = new IncomingOrder();
        holding.setExternalOrderNumber(externalOrderRequest.getExternalOrderNumber());
        holding.setPatientGuid(externalOrderRequest.getPatientGuid());
        holding.setPayload(payloadJson);
        holding.setReceivedTimestamp(new Timestamp(System.currentTimeMillis()));
        holding.setReceivedSysUserId(receivedSysUserId);

        holding.setSysUserId(receivedSysUserId);
        return baseObjectDAO.insert(holding);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrder> getOrders() {
        return baseObjectDAO.getAllOrdered("receivedTimestamp", true);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IncomingOrder> getOrderByExternalOrderNumber(String externalOrderNumber) {
        return baseObjectDAO.getByExternalOrderNumber(externalOrderNumber);
    }

    @Override
    @Transactional
    public IncomingOrder updateOrderByExternalOrderNumber(String externalOrderNumber, ExternalOrderRequest updatedRequest,
            String payloadJson, String updatedSysUserId) {
        IncomingOrder holding = baseObjectDAO.getByExternalOrderNumber(externalOrderNumber).orElse(null);
        if (holding == null) {
            throw new IllegalArgumentException("Unknown externalOrderNumber");
        }
        return updateExistingHolding(holding, updatedRequest, payloadJson, updatedSysUserId);
    }

    private IncomingOrder updateExistingHolding(IncomingOrder holding, ExternalOrderRequest updatedRequest,
            String payloadJson, String updatedSysUserId) {
        if (updatedRequest == null) {
            throw new IllegalArgumentException("Missing payload");
        }

        // immutable fields
        if (updatedRequest.getExternalOrderNumber() == null
                || !updatedRequest.getExternalOrderNumber().equals(holding.getExternalOrderNumber())) {
            throw new IllegalArgumentException("externalOrderNumber cannot be changed");
        }
        if (updatedRequest.getPatientGuid() == null || !updatedRequest.getPatientGuid().equals(holding.getPatientGuid())) {
            throw new IllegalArgumentException("patientGuid cannot be changed");
        }

        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            try {
                payloadJson = new ObjectMapper().writeValueAsString(updatedRequest);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON payload");
            }
        }

        holding.setPayload(payloadJson);
        holding.setSysUserId(updatedSysUserId);
        return baseObjectDAO.update(holding);
    }

    @Override
    @Transactional
    public ExternalOrderCollectResult collect(String externalOrderNumber, HttpServletRequest request) {
        if (externalOrderNumber == null || externalOrderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing externalOrderNumber");
        }

        IncomingOrder holding = baseObjectDAO.getByExternalOrderNumber(externalOrderNumber).orElse(null);

        if (holding == null) {
            ExternalOrderCollectResult result = new ExternalOrderCollectResult();
            Sample existingSample = sampleService.getSampleByReferringId(externalOrderNumber);
            if (existingSample != null) {
                result.setExternalOrderNumber(externalOrderNumber);
                result.setLabNo(existingSample.getAccessionNumber());
                result.setSampleId(existingSample.getId());
                return result;
            }
            result.setExternalOrderNumber(null);
            return result;
        }

        ExternalOrderRequest externalOrderRequest;
        try {
            externalOrderRequest = new ObjectMapper().readValue(holding.getPayload(), ExternalOrderRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid stored payload");
        }

        String payloadExternalOrderNumber = externalOrderRequest.getExternalOrderNumber();
        if (payloadExternalOrderNumber == null || !payloadExternalOrderNumber.equals(externalOrderNumber)) {
            throw new IllegalArgumentException("Stored payload externalOrderNumber mismatch");
        }

        Sample existingSample = null;
        if (payloadExternalOrderNumber != null) {
            existingSample = sampleService.getSampleByReferringId(payloadExternalOrderNumber);
        }

        if (existingSample != null) {
            baseObjectDAO.delete(holding);
            ExternalOrderCollectResult result = new ExternalOrderCollectResult();
            result.setExternalOrderNumber(payloadExternalOrderNumber);
            result.setLabNo(existingSample.getAccessionNumber());
            result.setSampleId(existingSample.getId());
            return result;
        }

        SamplePatientEntryForm form = externalOrderFormMapperService.buildForm(externalOrderRequest);
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "samplePatientEntryForm");

        try {
            samplePatientEntryOrderPlacementService.placeOrder(request, form, bindingResult);
        } catch (Exception e) {
            throw new IllegalStateException("Collect failed");
        }

        if (bindingResult.hasErrors()) {
            throw new IllegalArgumentException("Collect validation failed");
        }

        Sample createdSample = null;
        if (payloadExternalOrderNumber != null) {
            createdSample = sampleService.getSampleByReferringId(payloadExternalOrderNumber);
        }

        baseObjectDAO.delete(holding);

        ExternalOrderCollectResult result = new ExternalOrderCollectResult();
        result.setExternalOrderNumber(payloadExternalOrderNumber);
        if (createdSample != null) {
            result.setLabNo(createdSample.getAccessionNumber());
            result.setSampleId(createdSample.getId());
        } else {
            result.setLabNo(form.getSampleOrderItems() != null ? form.getSampleOrderItems().getLabNo() : null);
        }
        return result;
    }
}
