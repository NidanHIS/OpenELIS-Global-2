package org.openelisglobal.configuration.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks whether each configurable field belongs to the OCL package or to a
 * human.
 *
 * <p>
 * The OCL importer treats the shipped package as authoritative on every run, so
 * anything a lab changed in the UI gets written back the next time the import
 * fires. This is the record that lets the importer tell "the package changed"
 * apart from "a person changed it".
 *
 * <pre>
 *   UI save ────▶ markUserOwned(test, 42, [test_section])
 *                        │
 *   OCL import ──────────┴──▶ userOwnedFields(test, 42) = {test_section}
 *                              ├── test_section : skip, the lab owns it
 *                              ├── loinc        : apply, still OCL-owned
 *                              └── unit_of_measure : apply
 * </pre>
 *
 * <p>
 * Absence means OCL-owned, so a field nobody has touched stays managed by the
 * package and keeps receiving upstream corrections. Only a human edit writes a
 * row.
 *
 * <p>
 * Uses the {@link EntityManager} rather than a {@code JdbcTemplate} for the
 * same reason as {@link ConfigImportLogService}: {@code HibernateConfig} never
 * calls {@code setDataSource(..)} on its {@code JpaTransactionManager}, so a
 * JdbcTemplate would run outside the caller's transaction and could record
 * ownership for a write that later rolled back.
 */
@Service
public class FieldProvenanceService {

    public static final String ENTITY_TEST = "test";
    public static final String ENTITY_PANEL = "panel";

    /** Panel membership is set-valued, so the whole item list is one unit. */
    public static final String FIELD_PANEL_ITEMS = "items";

    public static final String FIELD_TEST_SECTION = "test_section";
    public static final String FIELD_LOINC = "loinc";
    public static final String FIELD_UOM = "unit_of_measure";
    public static final String FIELD_IS_ACTIVE = "is_active";

    private static final String OWNER_USER = "USER";
    private static final String OWNER_OCL = "OCL";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * The fields of this entity that a human owns. Anything not in the returned set
     * is still OCL-managed.
     *
     * <p>
     * Returned as a set rather than queried per field so the importer pays one
     * round trip per entity instead of one per field.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Set<String> userOwnedFields(String entityType, String entityId) {
        Query query = entityManager.createNativeQuery("SELECT field_name FROM clinlims.config_field_provenance"
                + " WHERE entity_type = :entityType AND entity_id = :entityId AND owner = :owner");
        query.setParameter("entityType", entityType);
        query.setParameter("entityId", entityId);
        query.setParameter("owner", OWNER_USER);
        Set<String> owned = new HashSet<>();
        for (Object row : query.getResultList()) {
            owned.add(String.valueOf(row));
        }
        return owned;
    }

    /** Convenience for the single-field case. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isUserOwned(String entityType, String entityId, String fieldName) {
        return userOwnedFields(entityType, entityId).contains(fieldName);
    }

    /**
     * Records that a human now owns these fields, so the importer must leave them
     * alone.
     *
     * <p>
     * Called from the ordinary save path rather than from each controller. A write
     * path that does not know this mechanism exists still produces the safe
     * outcome, because the default for an unrecorded field is OCL-owned only until
     * someone saves it.
     */
    @Transactional
    public void markUserOwned(String entityType, String entityId, Collection<String> fieldNames) {
        upsert(entityType, entityId, fieldNames, OWNER_USER);
    }

    /**
     * Hands fields back to the OCL package so the next import re-seeds them.
     *
     * <p>
     * Without this, provenance is a one-way door: one mistaken save during training
     * would detach a panel from its package permanently, and the only recovery
     * would be hand-written SQL against a live hospital database.
     */
    @Transactional
    public void markOclOwned(String entityType, String entityId, Collection<String> fieldNames) {
        upsert(entityType, entityId, fieldNames, OWNER_OCL);
    }

    private void upsert(String entityType, String entityId, Collection<String> fieldNames, String owner) {
        if (fieldNames == null || fieldNames.isEmpty()) {
            return;
        }
        for (String fieldName : fieldNames) {
            Query query = entityManager.createNativeQuery("INSERT INTO clinlims.config_field_provenance"
                    + " (entity_type, entity_id, field_name, owner, updated_at)"
                    + " VALUES (:entityType, :entityId, :fieldName, :owner, CURRENT_TIMESTAMP)"
                    + " ON CONFLICT (entity_type, entity_id, field_name)"
                    + " DO UPDATE SET owner = EXCLUDED.owner, updated_at = EXCLUDED.updated_at");
            query.setParameter("entityType", entityType);
            query.setParameter("entityId", entityId);
            query.setParameter("fieldName", fieldName);
            query.setParameter("owner", owner);
            query.executeUpdate();
        }
    }

    /** All governed fields for a test, for reset-to-package operations. */
    public static List<String> testFields() {
        return List.of(FIELD_TEST_SECTION, FIELD_LOINC, FIELD_UOM, FIELD_IS_ACTIVE);
    }
}
