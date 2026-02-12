package org.openelisglobal.dataexchange.externalorders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.List;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.dataexchange.externalorders.dao.ExternalOrderHoldingDAO;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.valueholder.ExternalOrderHolding;
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
public class ExternalOrderHoldingServiceImpl extends AuditableBaseObjectServiceImpl<ExternalOrderHolding, Integer>
        implements ExternalOrderHoldingService {

    @Autowired
    protected ExternalOrderHoldingDAO baseObjectDAO;

    @Autowired
    private ExternalOrderFormMapperService externalOrderFormMapperService;

    @Autowired
    private SamplePatientEntryOrderPlacementService samplePatientEntryOrderPlacementService;

    @Autowired
    private SampleService sampleService;

    ExternalOrderHoldingServiceImpl() {
        super(ExternalOrderHolding.class);
        this.auditTrailLog = false;
    }

    @Override
    protected ExternalOrderHoldingDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }

    @Override
    @Transactional
    public Integer receiveOrder(ExternalOrderRequest externalOrderRequest, String payloadJson, String receivedSysUserId) {
        ExternalOrderHolding holding = new ExternalOrderHolding();
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
    public List<ExternalOrderHolding> getOrders() {
        return baseObjectDAO.getAllOrdered("receivedTimestamp", true);
    }

    @Override
    @Transactional
    public ExternalOrderCollectResult collect(Integer holdingId, HttpServletRequest request) {
        ExternalOrderHolding holding = baseObjectDAO.get(holdingId).orElse(null);

        if (holding == null) {
            ExternalOrderCollectResult result = new ExternalOrderCollectResult();
            String externalOrderNumber = request != null ? request.getParameter("externalOrderNumber") : null;
            if (externalOrderNumber != null) {
                Sample existingSample = sampleService.getSampleByReferringId(externalOrderNumber);
                if (existingSample != null) {
                    result.setExternalOrderNumber(externalOrderNumber);
                    result.setLabNo(existingSample.getAccessionNumber());
                    result.setSampleId(existingSample.getId());
                    return result;
                }
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

        String externalOrderNumber = externalOrderRequest.getExternalOrderNumber();

        Sample existingSample = null;
        if (externalOrderNumber != null) {
            existingSample = sampleService.getSampleByReferringId(externalOrderNumber);
        }

        if (existingSample != null) {
            baseObjectDAO.delete(holding);
            ExternalOrderCollectResult result = new ExternalOrderCollectResult();
            result.setExternalOrderNumber(externalOrderNumber);
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
        if (externalOrderNumber != null) {
            createdSample = sampleService.getSampleByReferringId(externalOrderNumber);
        }

        baseObjectDAO.delete(holding);

        ExternalOrderCollectResult result = new ExternalOrderCollectResult();
        result.setExternalOrderNumber(externalOrderNumber);
        if (createdSample != null) {
            result.setLabNo(createdSample.getAccessionNumber());
            result.setSampleId(createdSample.getId());
        } else {
            result.setLabNo(form.getSampleOrderItems() != null ? form.getSampleOrderItems().getLabNo() : null);
        }
        return result;
    }
}
