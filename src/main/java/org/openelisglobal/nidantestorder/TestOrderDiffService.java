package org.openelisglobal.nidantestorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openelisglobal.dataexchange.externalorders.dto.ExternalOrderRequest;
import org.openelisglobal.dataexchange.externalorders.service.IncomingOrderService;
import org.openelisglobal.dataexchange.externalorders.valueholder.IncomingOrder;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.service.PanelItemService;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Computes the net-new tests in a collected sample relative to what was already
 * known from the original external order held in incoming_orders.
 *
 * <p>Rules:
 * <ul>
 *   <li>If the sample has NO externalOrderNumber (manual ELIS entry) → all tests
 *       are net-new; caller should send the full list as-is.</li>
 *   <li>If the externalOrderNumber IS set → fetch the original holding, expand
 *       its tests + panels into a canonical key-set, then return only the
 *       collected tests whose key is NOT in that set.</li>
 *   <li>If the holding is missing (race / already finalized) → treat
 *       conservatively: return empty list so we send nothing (safer than
 *       re-duplicating).</li>
 * </ul>
 *
 * <p>Canonical key precedence: guid wins over loinc.
 * Key format: {@code "guid:<value>"} or {@code "loinc:<value>"}.
 */
@Component
public class TestOrderDiffService {

    private static final Logger LOG = LogManager.getLogger(TestOrderDiffService.class);

    @Autowired
    private IncomingOrderService incomingOrderService;

    @Autowired
    private TestService testService;

    @Autowired
    private PanelService panelService;

    @Autowired
    private PanelItemService panelItemService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Result of a diff operation.
     *
     * @param isExternalOrder {@code true} when the sample originated from an
     *                        external order (visitUuid was set)
     * @param netNewTests     tests to forward to middleware — empty means nothing
     *                        to send
     */
    public record DiffResult(boolean isExternalOrder, List<TestOrderNotification.TestRef> netNewTests) {
    }

    /**
     * Computes the diff.
     *
     * @param visitUuid      {@code updateData.getReferringId()} — null/blank for
     *                       manual entries
     * @param collectedTests full list of TestRefs resolved by the event listener
     * @return DiffResult — never null
     */
    public DiffResult diff(String visitUuid, List<TestOrderNotification.TestRef> collectedTests) {

        // ── Manual entry — nothing to diff, send everything ──────────────────────────
        if (visitUuid == null || visitUuid.isBlank()) {
            LOG.debug("[NIDAN-DIFF] visitUuid=null → manual entry, no diff needed");
            return new DiffResult(false, collectedTests != null ? collectedTests : List.of());
        }

        LOG.info("[NIDAN-DIFF] externalOrder detected visitUuid={}", visitUuid);

        // ── Fetch the original holding ────────────────────────────────────────────────
        Optional<IncomingOrder> holdingOpt = incomingOrderService.getOrderByExternalOrderNumber(visitUuid);
        if (holdingOpt.isEmpty()) {
            // Row already finalized or never existed — play it safe, send nothing
            LOG.warn("[NIDAN-DIFF] holding not found for visitUuid={} — suppressing notification (safe)", visitUuid);
            return new DiffResult(true, List.of());
        }

        IncomingOrder holding = holdingOpt.get();
        ExternalOrderRequest original;
        try {
            original = objectMapper.readValue(holding.getPayload(), ExternalOrderRequest.class);
        } catch (Exception e) {
            LOG.error("[NIDAN-DIFF] failed to parse holding payload for visitUuid={}: {}", visitUuid, e.getMessage());
            // Corrupt payload — suppress to avoid re-duplicating unknown state
            return new DiffResult(true, List.of());
        }

        // ── Build canonical key-set from the original order ───────────────────────────
        Set<String> originalKeys = buildOriginalKeySet(original, visitUuid);
        LOG.info("[NIDAN-DIFF] original key-set size={} for visitUuid={}", originalKeys.size(), visitUuid);

        // ── Filter collected tests — keep only net-new ones ───────────────────────────
        List<TestOrderNotification.TestRef> netNew = new ArrayList<>();
        for (TestOrderNotification.TestRef ref : collectedTests != null ? collectedTests : List.<TestOrderNotification.TestRef>of()) {
            String key = canonicalKey(ref.testGuid(), ref.loincCode());
            if (key == null) {
                // No identifier at all — can't match, treat as net-new to avoid data loss
                LOG.debug("[NIDAN-DIFF] TestRef with no guid/loinc — treating as net-new");
                netNew.add(ref);
                continue;
            }
            if (originalKeys.contains(key)) {
                LOG.debug("[NIDAN-DIFF] suppressing duplicate key={}", key);
            } else {
                LOG.info("[NIDAN-DIFF] net-new test key={}", key);
                netNew.add(ref);
            }
        }

        LOG.info("[NIDAN-DIFF] result: collected={} original={} netNew={} for visitUuid={}",
                collectedTests != null ? collectedTests.size() : 0,
                originalKeys.size(),
                netNew.size(),
                visitUuid);

        return new DiffResult(true, netNew);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Builds the set of canonical keys for every test in the original
     * ExternalOrderRequest — direct tests AND panel-expanded tests.
     */
    private Set<String> buildOriginalKeySet(ExternalOrderRequest original, String visitUuid) {
        Set<String> keys = new HashSet<>();

        if (original.getSamples() == null) {
            return keys;
        }

        for (ExternalOrderRequest.ExternalOrderSample sample : original.getSamples()) {
            // Direct tests
            if (sample.getTests() != null) {
                for (ExternalOrderRequest.ExternalOrderTestRef testRef : sample.getTests()) {
                    String key = canonicalKey(testRef.getTestGuid(), testRef.getLoinc());
                    if (key != null) {
                        keys.add(key);
                    }
                }
            }

            // Panels — expand to individual test keys
            if (sample.getPanels() != null) {
                for (ExternalOrderRequest.ExternalOrderPanelRef panelRef : sample.getPanels()) {
                    expandPanelIntoKeys(panelRef, keys, visitUuid);
                }
            }
        }

        return keys;
    }

    /**
     * Resolves a panel reference to its constituent tests and adds their canonical
     * keys to the provided set.
     */
    private void expandPanelIntoKeys(ExternalOrderRequest.ExternalOrderPanelRef panelRef,
            Set<String> keys, String visitUuid) {
        Panel panel = resolvePanel(panelRef);
        if (panel == null) {
            LOG.warn("[NIDAN-DIFF] could not resolve panel panelGuid={} loinc={} for visitUuid={}",
                    panelRef.getPanelGuid(), panelRef.getLoinc(), visitUuid);
            return;
        }

        List<PanelItem> items = panelItemService.getPanelItemsForPanel(panel.getId());
        if (items == null) {
            return;
        }

        for (PanelItem pi : items) {
            if (pi.getTest() == null || pi.getTest().getId() == null) {
                continue;
            }
            // Fetch the full Test to get guid + loinc
            Test full = testService.get(pi.getTest().getId());
            if (full == null) {
                continue;
            }
            String key = canonicalKey(full.getGuid(), full.getLoinc());
            if (key != null) {
                keys.add(key);
            }
        }
    }

    /** Resolves a panel by GUID first, then by LOINC. Returns null if not found. */
    private Panel resolvePanel(ExternalOrderRequest.ExternalOrderPanelRef panelRef) {
        if (panelRef.getPanelGuid() != null && !panelRef.getPanelGuid().isBlank()) {
            Panel p = panelService.getPanelByGUID(panelRef.getPanelGuid().trim());
            if (p != null) {
                return p;
            }
        }
        if (panelRef.getLoinc() != null && !panelRef.getLoinc().isBlank()) {
            return panelService.getPanelByLoincCode(panelRef.getLoinc().trim());
        }
        return null;
    }

    /**
     * Canonical key: guid wins, loinc is fallback. Returns null if both absent.
     * All comparisons are trim + lowercase to be resilient to whitespace/case.
     */
    private static String canonicalKey(String guid, String loinc) {
        if (guid != null && !guid.isBlank()) {
            return "guid:" + guid.trim().toLowerCase();
        }
        if (loinc != null && !loinc.isBlank()) {
            return "loinc:" + loinc.trim().toLowerCase();
        }
        return null;
    }
}
