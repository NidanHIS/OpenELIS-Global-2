package org.openelisglobal.nidanpaywall;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.openelisglobal.siteinformation.service.SiteInformationDomainService;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads the three user-configurable paywall bypass flags from
 * {@code site_information} and exposes a simple set of allowed visit-type
 * tokens.
 *
 * <p>
 * Canonical token values (upper-case, no whitespace):
 * <ul>
 * <li>{@code "OPD"} — controlled by {@code nidan_paywall_allow_opd}</li>
 * <li>{@code "IPD"} — controlled by {@code nidan_paywall_allow_ipd}</li>
 * <li>{@code "ER"} — controlled by {@code nidan_paywall_allow_er}</li>
 * </ul>
 *
 * <p>
 * Values are read from the DB on every call — no in-process cache — so an admin
 * change takes effect on the very next paywall-check request without requiring
 * a restart. The three DB reads are tiny indexed lookups; the added latency is
 * negligible compared to the Odoo HTTP call that follows.
 *
 * <p>
 * If a row is missing (e.g. fresh install before Liquibase has run), the flag
 * defaults to {@code false} — paywall enforced — which is the safe direction.
 */
@Service
public class NidanPaywallConfigService {

    @Autowired
    private SiteInformationService siteInformationService;

    @Autowired
    private SiteInformationDomainService siteInformationDomainService;

    private static final Object lock = new Object();

    public String getConfigKeyForVisitType(String visitType) {
        if (visitType == null) {
            return "";
        }
        return "nidan_paywall_allow_visit_type_" + visitType.trim().toLowerCase();
    }

    private String capitalize(String str) {
        if (str == null || str.trim().isEmpty()) {
            return str;
        }
        String[] words = str.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public Map<String, Boolean> getConfig() {
        List<SiteInformation> list = siteInformationService.getSiteInformationByDomainName("nidanPaywallConfig");
        Map<String, Boolean> map = new TreeMap<>();
        if (list != null) {
            for (SiteInformation si : list) {
                if (si.getName() != null && si.getName().startsWith("nidan_paywall_allow_visit_type_")) {
                    map.put(si.getDescription(), "true".equalsIgnoreCase(si.getValue()));
                }
            }
        }
        return map;
    }

    public void updateConfig(Map<String, Boolean> config) {
        if (config == null)
            return;
        for (Map.Entry<String, Boolean> entry : config.entrySet()) {
            String visitType = entry.getKey();
            Boolean value = entry.getValue();
            if (visitType == null)
                continue;

            String key = getConfigKeyForVisitType(visitType);
            SiteInformation si = siteInformationService.getSiteInformationByName(key);
            if (si != null) {
                si.setValue(value != null && value ? "true" : "false");
                siteInformationService.persistData(si, false);
            } else {
                si = new SiteInformation();
                si.setName(key);
                si.setDescription(visitType);
                si.setValue(value != null && value ? "true" : "false");
                si.setValueType("boolean");
                si.setEncrypted(false);
                si.setDomain(siteInformationDomainService.getByName("nidanPaywallConfig"));
                siteInformationService.persistData(si, true);
            }
        }
    }

    public boolean isVisitTypeAllowed(String visitType) {
        if (visitType == null || visitType.trim().isEmpty()) {
            return false;
        }
        String key = getConfigKeyForVisitType(visitType);
        SiteInformation si = siteInformationService.getSiteInformationByName(key);
        if (si == null) {
            synchronized (lock) {
                si = siteInformationService.getSiteInformationByName(key);
                if (si == null) {
                    si = new SiteInformation();
                    si.setName(key);
                    si.setDescription(capitalize(visitType));
                    si.setValue("false");
                    si.setValueType("boolean");
                    si.setEncrypted(false);
                    si.setDomain(siteInformationDomainService.getByName("nidanPaywallConfig"));
                    siteInformationService.persistData(si, true);
                }
            }
        }
        return si != null && "true".equalsIgnoreCase(si.getValue());
    }
}
