package org.openelisglobal.panel.event.listener;

import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.odoo.service.OdooPanelComboService;
import org.openelisglobal.odoo.service.OdooPanelProductService;
import org.openelisglobal.panel.event.PanelCreatedOrUpdatedEvent;
import org.openelisglobal.panel.service.fhir.PanelFhirTransformService;
import org.openelisglobal.panel.valueholder.Panel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@SuppressWarnings("unused")
public class PanelCreatedOrUpdatedEventListener {

    @Autowired
    private OdooPanelProductService odooPanelProductService;
    @Autowired
    private OdooPanelComboService odooPanelComboService;

    @Autowired(required = false)
    private PanelFhirTransformService panelFhirTransformService;

    @Value("${org.openelisglobal.fhir.panel.sync.enabled:false}")
    private boolean panelFhirSyncEnabled;

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

            // Existing Odoo sync
            odooPanelProductService.syncPanelToOdoo(panel);
            odooPanelComboService.syncPanelComboToOdoo(panel);

            // NEW: FHIR sync
            if (panelFhirSyncEnabled && panelFhirTransformService != null) {
                LogEvent.logInfo(this.getClass().getSimpleName(), "handlePanelCreatedOrUpdatedEvent",
                        "Syncing panel to FHIR: " + panel.getDescription() + " (guid=" + panel.getGuid() + ")");
                panelFhirTransformService.syncPanelToFhir(panel);
            }

        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "handlePanelCreatedOrUpdatedEvent",
                    "Error processing panel event for panel "
                            + (event.getPanel() != null ? event.getPanel().getId() : "unknown") + ": "
                            + e.getMessage());
            LogEvent.logError(this.getClass().getSimpleName(), "handlePanelCreatedOrUpdatedEvent",
                    "Full stack trace: " + e.toString());
        }
    }
}
