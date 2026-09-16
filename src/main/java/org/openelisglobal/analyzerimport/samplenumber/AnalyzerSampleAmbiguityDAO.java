package org.openelisglobal.analyzerimport.samplenumber;

import java.util.Collections;
import java.util.List;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

interface AnalyzerSampleAmbiguityDAO extends BaseDAO<AnalyzerSampleAmbiguity, Integer> {
    List<AnalyzerSampleAmbiguity> findByStatus(String status);
}

@Component
@Transactional
class AnalyzerSampleAmbiguityDAOImpl extends BaseDAOImpl<AnalyzerSampleAmbiguity, Integer>
        implements AnalyzerSampleAmbiguityDAO {

    AnalyzerSampleAmbiguityDAOImpl() {
        super(AnalyzerSampleAmbiguity.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyzerSampleAmbiguity> findByStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<AnalyzerSampleAmbiguity> rows = entityManager.createQuery(
                    "from AnalyzerSampleAmbiguity e where e.status = :status order by e.id desc",
                    AnalyzerSampleAmbiguity.class).setParameter("status", status).getResultList();
            return rows != null ? rows : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
