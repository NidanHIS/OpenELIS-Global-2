package org.openelisglobal.dataexchange.externalorders.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;

public interface IncomingOrderService {

    Integer receiveOrder(ExternalOrderRequest externalOrderRequest, String payloadJson, String receivedSysUserId);

    List<IncomingOrder> getOrders();

    Optional<IncomingOrder> getOrderByExternalOrderNumber(String externalOrderNumber);

    IncomingOrder updateOrderByExternalOrderNumber(String externalOrderNumber, ExternalOrderRequest updatedRequest,
            String payloadJson, String updatedSysUserId);

    ExternalOrderCollectResult collect(String externalOrderNumber, HttpServletRequest request);
}
