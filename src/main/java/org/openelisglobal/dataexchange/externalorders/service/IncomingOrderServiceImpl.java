package org.openelisglobal.dataexchange.externalorders.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.dataexchange.externalorders.dao.IncomingOrderDAO;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomingOrderServiceImpl extends AuditableBaseObjectServiceImpl<IncomingOrder, Integer>
        implements IncomingOrderService {

    @Autowired
    protected IncomingOrderDAO baseObjectDAO;

    @Autowired
    private ExternalOrderFormMapperService externalOrderFormMapperService;

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
    public void finalizeHolding(String externalOrderNumber) {
        if (externalOrderNumber == null || externalOrderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing externalOrderNumber");
        }

        IncomingOrder holding = baseObjectDAO.getByExternalOrderNumber(externalOrderNumber).orElse(null);
        if (holding != null) {
            baseObjectDAO.delete(holding);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SamplePatientEntryForm buildSamplePatientEntryForm(String externalOrderNumber) {
        if (externalOrderNumber == null || externalOrderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing externalOrderNumber");
        }

        IncomingOrder holding = baseObjectDAO.getByExternalOrderNumber(externalOrderNumber).orElse(null);
        if (holding == null) {
            throw new IllegalArgumentException("Unknown externalOrderNumber");
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

        return externalOrderFormMapperService.buildForm(externalOrderRequest);
    }
}
