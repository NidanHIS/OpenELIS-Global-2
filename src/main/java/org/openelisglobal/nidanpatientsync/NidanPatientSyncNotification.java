package org.openelisglobal.nidanpatientsync;

/**
 * Payload sent to nidan-cis {@code POST /openelis/patient}.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code guid}               — OpenELIS patient UUID (anchor, always present)</li>
 *   <li>{@code firstName}          — given name (may be blank)</li>
 *   <li>{@code lastName}           — family name (may be blank)</li>
 *   <li>{@code gender}             — M/F/O (may be blank)</li>
 *   <li>{@code birthDateForDisplay} — dd/MM/yyyy format (may be blank)</li>
 *   <li>{@code nationalId}         — NID; null/blank when not provided by user</li>
 *   <li>{@code changeType}         — "created" or "updated"</li>
 * </ul>
 *
 * <p>subjectNumber (health ID) is intentionally NOT included — Odoo and OpenMRS
 * own and supply that value; OpenELIS never originates it.
 */
public record NidanPatientSyncNotification(
        String guid,
        String firstName,
        String lastName,
        String gender,
        String birthDateForDisplay,
        String nationalId,
        String changeType) {
}
