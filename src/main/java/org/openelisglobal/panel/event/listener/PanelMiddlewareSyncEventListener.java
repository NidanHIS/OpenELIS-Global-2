package org.openelisglobal.panel.event.listener;

import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.panel.event.PanelCreatedOrUpdatedEvent;
import org.openelisglobal.panel.service.middleware.PanelMiddlewareSyncService;
import org.openelisglobal.panel.valueholder.Panel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PanelMiddlewareSyncEventListener {

    @Autowired
    private PanelMiddlewareSyncService panelMiddlewareSyncService;

    @Async
    // AFTER_COMMIT: the panel write must be committed before Odoo or CIS is told
    // about
    // it. With a plain @EventListener this fired at publish time, so an @Async
    // thread
    // could read uncommitted state (or a detached entity) from a session it does
    // not
    // own. fallbackExecution keeps the listener firing for any publisher that is
    // not
    // inside a transaction, which is how it behaved before.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handlePanelCreatedOrUpdatedEvent(PanelCreatedOrUpdatedEvent event) {
        try {
            Panel panel = event.getPanel();
            LogEvent.logInfo(this.getClass().getSimpleName(), "handlePanelCreatedOrUpdatedEvent",
                    "Syncing panel to middleware: " + panel.getDescription() + " (guid=" + panel.getGuid() + ")");

            panelMiddlewareSyncService.syncPanelToMiddleware(panel);

        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "handlePanelCreatedOrUpdatedEvent",
                    "Error processing PanelCreatedOrUpdatedEvent for middleware sync: " + e.getMessage());
            LogEvent.logError(this.getClass().getSimpleName(), "handlePanelCreatedOrUpdatedEvent",
                    "Full stack trace: " + e.toString());
        }
    }
}
