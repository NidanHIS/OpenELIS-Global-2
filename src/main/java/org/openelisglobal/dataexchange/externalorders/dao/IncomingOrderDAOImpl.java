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
        StringBuilder hql = new StringBuilder("from IncomingOrder e where 1=1");
        if (from != null) {
            hql.append(" and e.receivedTimestamp >= :from");
        }
        if (to != null) {
            hql.append(" and e.receivedTimestamp < :to");
        }
        if (search != null && !search.trim().isEmpty()) {
            // Match on order number OR patient national ID OR patient last/first name.
            //
            // Join path (mirrors PatientServiceImpl.getPatientForGuid exactly):
            // IncomingOrder.patientGuid
            // → PatientIdentity.identityData (WHERE identityTypeId matches type 'GUID')
            // → PatientIdentity.patientId = Patient.id
            // → Patient.nationalId (searchable)
            // → Patient.person → Person.lastName / Person.firstName (searchable)
            //
            // Cross-join style (pi.patientId = p.id) is required because PatientIdentity
            // has no Hibernate association to Patient — same pattern as SampleHumanDAOImpl.
            // PatientIdentityType.identityType = 'GUID' is the stable string key used
            // throughout the codebase (PatientServiceImpl, ResultReportingCollator, etc.).
            hql.append(" and (lower(e.externalOrderNumber) like :search" + " or exists ("
                    + "select pi.id from PatientIdentity pi, Patient p, PatientIdentityType pit" + " join p.person per"
                    + " where pi.identityData = e.patientGuid" + " and pi.identityTypeId = pit.id"
                    + " and pit.identityType = 'GUID'" + " and pi.patientId = p.id"
                    + " and (lower(p.nationalId) like :search" + " or lower(per.lastName) like :search"
                    + " or lower(per.firstName) like :search" + " or exists ("
                    + "select pi2.id from PatientIdentity pi2, PatientIdentityType pit2"
                    + " where pi2.patientId = p.id" + " and pi2.identityTypeId = pit2.id"
                    + " and pit2.identityType = 'SUBJECT'" + " and lower(pi2.identityData) like :search)"
                    + ")))");
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
        StringBuilder hql = new StringBuilder("select count(e) from IncomingOrder e where 1=1");
        if (from != null) {
            hql.append(" and e.receivedTimestamp >= :from");
        }
        if (to != null) {
            hql.append(" and e.receivedTimestamp < :to");
        }
        if (search != null && !search.trim().isEmpty()) {
            // Identical search clause as getPagedOrders — keeps count consistent with
            // results.
            hql.append(" and (lower(e.externalOrderNumber) like :search" + " or exists ("
                    + "select pi.id from PatientIdentity pi, Patient p, PatientIdentityType pit" + " join p.person per"
                    + " where pi.identityData = e.patientGuid" + " and pi.identityTypeId = pit.id"
                    + " and pit.identityType = 'GUID'" + " and pi.patientId = p.id"
                    + " and (lower(p.nationalId) like :search" + " or lower(per.lastName) like :search"
                    + " or lower(per.firstName) like :search" + " or exists ("
                    + "select pi2.id from PatientIdentity pi2, PatientIdentityType pit2"
                    + " where pi2.patientId = p.id" + " and pi2.identityTypeId = pit2.id"
                    + " and pit2.identityType = 'SUBJECT'" + " and lower(pi2.identityData) like :search)"
                    + ")))");
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
