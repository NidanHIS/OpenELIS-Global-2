package org.openelisglobal.dataexchange.externalorders.dao;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class IncomingOrderDAOImpl extends BaseDAOImpl<IncomingOrder, Integer> implements IncomingOrderDAO {

    public IncomingOrderDAOImpl() {
        super(IncomingOrder.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IncomingOrder> getByExternalOrderNumber(String externalOrderNumber) {
        if (externalOrderNumber == null || externalOrderNumber.trim().isEmpty()) {
            return Optional.empty();
        }

        List<IncomingOrder> result = entityManager
                .createQuery("from IncomingOrder e where e.externalOrderNumber = :externalOrderNumber",
                        IncomingOrder.class)
                .setParameter("externalOrderNumber", externalOrderNumber).setMaxResults(1).getResultList();

        return result == null || result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingOrder> getPagedOrders(Timestamp from, Timestamp to, String search, int offset, int limit) {
        StringBuilder hql = new StringBuilder(
                "from IncomingOrder e where 1=1");
        if (from != null) {
            hql.append(" and e.receivedTimestamp >= :from");
        }
        if (to != null) {
            hql.append(" and e.receivedTimestamp < :to");
        }
        if (search != null && !search.trim().isEmpty()) {
            hql.append(" and lower(e.externalOrderNumber) like :search");
        }
        hql.append(" order by e.receivedTimestamp desc");

        var query = entityManager.createQuery(hql.toString(), IncomingOrder.class);
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim().toLowerCase() + "%");
        }
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countOrders(Timestamp from, Timestamp to, String search) {
        StringBuilder hql = new StringBuilder(
                "select count(e) from IncomingOrder e where 1=1");
        if (from != null) {
            hql.append(" and e.receivedTimestamp >= :from");
        }
        if (to != null) {
            hql.append(" and e.receivedTimestamp < :to");
        }
        if (search != null && !search.trim().isEmpty()) {
            hql.append(" and lower(e.externalOrderNumber) like :search");
        }

        var query = entityManager.createQuery(hql.toString(), Long.class);
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        if (search != null && !search.trim().isEmpty()) {
            query.setParameter("search", "%" + search.trim().toLowerCase() + "%");
        }
        return query.getSingleResult();
    }
}
