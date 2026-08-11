package org.openelisglobal.nidanlabtestremarks.daoimpl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.nidanlabtestremarks.dao.LabTestRemarkDAO;
import org.openelisglobal.nidanlabtestremarks.valueholder.LabTestRemark;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class LabTestRemarkDAOImpl implements LabTestRemarkDAO {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<LabTestRemark> getAll() {
        try {
            String jpql = "SELECT r FROM LabTestRemark r ORDER BY r.id ASC";
            TypedQuery<LabTestRemark> query = entityManager.createQuery(jpql, LabTestRemark.class);
            return query.getResultList();
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkDAO getAll()", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LabTestRemark getByEntityTypeAndId(String entityType, Long entityId) {
        try {
            String jpql = "SELECT r FROM LabTestRemark r WHERE r.entityType = :type AND r.entityId = :eid";
            TypedQuery<LabTestRemark> query = entityManager.createQuery(jpql, LabTestRemark.class);
            query.setParameter("type", entityType);
            query.setParameter("eid", entityId);
            return query.getResultList().stream().findFirst().orElse(null);
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkDAO getByEntityTypeAndId()", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LabTestRemark getById(Long id) {
        try {
            return entityManager.find(LabTestRemark.class, id);
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkDAO getById()", e);
        }
    }

    @Override
    public void saveOrUpdate(LabTestRemark remark) {
        try {
            remark.setLastupdated(Timestamp.from(Instant.now()));
            if (remark.getId() == null) {
                entityManager.persist(remark);
            } else {
                entityManager.merge(remark);
            }
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkDAO saveOrUpdate()", e);
        }
    }

    @Override
    public void deleteById(Long id) {
        try {
            LabTestRemark remark = entityManager.find(LabTestRemark.class, id);
            if (remark != null) {
                entityManager.remove(remark);
            }
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            throw new LIMSRuntimeException("Error in LabTestRemarkDAO deleteById()", e);
        }
    }
}
