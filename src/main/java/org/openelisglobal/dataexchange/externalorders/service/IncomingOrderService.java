package org.openelisglobal.dataexchange.externalorders.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.openelisglobal.sample.form.SamplePatientEntryForm;

public interface IncomingOrderService {

    Integer receiveOrder(ExternalOrderRequest externalOrderRequest, String payloadJson, String receivedSysUserId);

    IncomingOrder receiveOrMergeOrder(ExternalOrderRequest externalOrderRequest, String payloadJson,
            String receivedSysUserId);

    /**
     * Returns ALL orders ordered by receivedTimestamp desc. Existing callers
     * unchanged.
     */
    List<IncomingOrder> getOrders();

    /**
     * Returns a single page of orders with optional date-range and search filters.
     *
     * @param from     inclusive lower bound on receivedTimestamp (nullable = no
     *                 lower bound)
     * @param to       exclusive upper bound on receivedTimestamp (nullable = no
     *                 upper bound)
     * @param search   optional substring to match against externalOrderNumber
     *                 (nullable = no filter)
     * @param page     1-based page number
     * @param pageSize number of records per page (capped at 100 internally)
     */
    PagedIncomingOrders getOrdersPage(Timestamp from, Timestamp to, String search, int page, int pageSize);

    Optional<IncomingOrder> getOrderByExternalOrderNumber(String externalOrderNumber);

    IncomingOrder updateOrderByExternalOrderNumber(String externalOrderNumber, ExternalOrderRequest updatedRequest,
            String payloadJson, String updatedSysUserId);

    void finalizeHolding(String externalOrderNumber);

    void deleteHoldingByExternalOrderNumber(String externalOrderNumber);

    SamplePatientEntryForm buildSamplePatientEntryForm(String externalOrderNumber);

    /** Immutable result wrapper returned by {@link #getOrdersPage}. */
    final class PagedIncomingOrders {
        private final List<IncomingOrder> items;
        private final long totalCount;
        private final int page;
        private final int pageSize;
        private final int totalPages;

        public PagedIncomingOrders(List<IncomingOrder> items, long totalCount, int page, int pageSize) {
            this.items = items;
            this.totalCount = totalCount;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;
        }

        public List<IncomingOrder> getItems() {
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
