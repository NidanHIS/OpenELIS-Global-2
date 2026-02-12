package org.openelisglobal.dataexchange.externalorders.service;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.valueholder.ExternalOrderHolding;

public interface ExternalOrderHoldingService {

    Integer receiveOrder(ExternalOrderRequest externalOrderRequest, String payloadJson, String receivedSysUserId);

    List<ExternalOrderHolding> getOrders();

    ExternalOrderCollectResult collect(Integer holdingId, HttpServletRequest request);
}
