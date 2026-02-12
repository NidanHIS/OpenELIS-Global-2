package org.openelisglobal.dataexchange.externalorders.dao;

import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.dataexchange.externalorders.valueholder.ExternalOrderHolding;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ExternalOrderHoldingDAOImpl extends BaseDAOImpl<ExternalOrderHolding, Integer>
        implements ExternalOrderHoldingDAO {

    public ExternalOrderHoldingDAOImpl() {
        super(ExternalOrderHolding.class);
    }
}
