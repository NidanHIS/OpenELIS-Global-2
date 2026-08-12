package org.openelisglobal.nidanlabtestremarks.dao;

import java.util.List;
import org.openelisglobal.nidanlabtestremarks.valueholder.LabTestRemark;

public interface LabTestRemarkDAO {

    List<LabTestRemark> getAll();

    LabTestRemark getByEntityTypeAndId(String entityType, Long entityId);

    LabTestRemark getById(Long id);

    void saveOrUpdate(LabTestRemark remark);

    void deleteById(Long id);
}
