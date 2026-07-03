package org.openelisglobal.nidancisretry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Owns the @Async boundary for CIS dead-letter retry.
 * Controller calls {@link #retryAsync()} — Spring dispatches it to the task executor
 * and the HTTP call blocks returns immediately to the caller.
 *
 * <p>Follows the same ELIS @Async pattern as TestOrderEventListener, AsyncExternalSender,
 * FhirTransformServiceImpl, etc.
 */
@Service
public class NidanCisRetryService {

    private static final Logger LOG = LogManager.getLogger(NidanCisRetryService.class);

    @Autowired
    private NidanCisRetryClient cisRetryClient;

    /**
     * Executes CIS dead-letter retry asynchronously via Spring's task executor.
     * Returns immediately; actual HTTP work runs on a Spring-managed thread.
     */
    @Async
    public void retryAsync() {
        LOG.info("[NIDAN-CIS-RETRY] retryAsync dispatched to Spring task executor");
        cisRetryClient.execute();
    }
}
