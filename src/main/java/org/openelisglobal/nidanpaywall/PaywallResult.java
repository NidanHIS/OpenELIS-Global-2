package org.openelisglobal.nidanpaywall;

/**
 * Typed result from {@link NidanPaywallClient#check}.
 *
 * <p>{@code decision} values:
 * <ul>
 *   <li>{@code "allow"}  — patient is clear, proceed.</li>
 *   <li>{@code "block"}  — patient owes money, stop the action.</li>
 *   <li>{@code "outage"} — Odoo unreachable, fail-open (allow but log).</li>
 * </ul>
 */
public record PaywallResult(
        String decision,
        double outstandingAmount,
        String currency,
        String paymentStatus) {

    public boolean isBlocked() {
        return "block".equals(decision);
    }

    // --- factories ---

    static PaywallResult allow() {
        return new PaywallResult("allow", 0.0, null, null);
    }

    static PaywallResult outage() {
        return new PaywallResult("outage", 0.0, null, null);
    }
}
