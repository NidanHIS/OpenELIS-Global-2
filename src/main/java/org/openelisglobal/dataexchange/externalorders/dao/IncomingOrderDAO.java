package org.openelisglobal.dataexchange.externalorders.dao;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;

public interface IncomingOrderDAO extends BaseDAO<IncomingOrder, Integer> {

    Optional<IncomingOrder> getByExternalOrderNumber(String externalOrderNumber);

    /**
     * Returns a page of IncomingOrder rows ordered by receivedTimestamp descending.
     * Both {@code from} and {@code to} are optional — pass null to omit that bound.
     * {@code search} is optional — pass null or blank to skip text filtering. Text
     * filtering matches against externalOrderNumber only (no patient join here;
     * patient name search is handled at the service layer after enrichment).
     *
     * @param from   inclusive lower bound on receivedTimestamp (nullable)
     * @param to     exclusive upper bound on receivedTimestamp (nullable)
     * @param search optional substring match on externalOrderNumber (nullable)
     * @param offset zero-based row offset (for OFFSET clause)
     * @param limit  maximum number of rows to return
     */
    List<IncomingOrder> getPagedOrders(Timestamp from, Timestamp to, String search, int offset, int limit);

    /**
     * Returns the total count matching the same filters as {@link #getPagedOrders}.
     * Used to populate totalCount in the paginated response envelope.
     */
    long countOrders(Timestamp from, Timestamp to, String search);
}
