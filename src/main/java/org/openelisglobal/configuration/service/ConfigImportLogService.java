package org.openelisglobal.configuration.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, transactional record of which configuration packages have been
 * imported.
 *
 * <p>
 * This exists because {@code ocl-checksums.properties} is not a durable marker.
 * {@code ConfigurationInitializationService.saveChecksums} writes it with a
 * plain {@code FileWriter} (no fsync, no atomic rename) and only once the whole
 * domain has finished. Three separate windows leave it missing or zero-length
 * after an unclean shutdown, and on the next boot the import re-runs and
 * overwrites lab-owned configuration:
 *
 * <pre>
 *   import starts ──┬──── power cut here: data written, no marker      (widest)
 *                   │
 *   import ends ────┼──── power cut here: data committed, no marker
 *                   │
 *   file written ───┴──── power cut inside the OS writeback window:
 *                         file comes back zero-length
 * </pre>
 *
 * <p>
 * Reads and writes here go through the {@link EntityManager}, deliberately, not
 * a {@code JdbcTemplate}. {@code HibernateConfig} builds its
 * {@code JpaTransactionManager} with only {@code setEntityManagerFactory(..)}
 * and never {@code setDataSource(..)}, so {@code DataSourceUtils} cannot hand a
 * {@code JdbcTemplate} the transaction-bound connection. A JdbcTemplate here
 * would quietly run on its own connection and commit independently, which would
 * defeat the entire point of this class.
 */
@Service
public class ConfigImportLogService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * True when this exact file content has already been imported for this domain.
     *
     * <p>
     * Runs in the caller's transaction when there is one. Read-only, so it is safe
     * either way.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean isAlreadyImported(String domain, String fileName, String checksum) {
        Query query = entityManager.createNativeQuery("SELECT checksum FROM clinlims.config_import_log"
                + " WHERE domain = :domain AND file_name = :fileName");
        query.setParameter("domain", domain);
        query.setParameter("fileName", fileName);
        List<?> rows = query.getResultList();
        return !rows.isEmpty() && checksum.equals(String.valueOf(rows.get(0)));
    }

    /**
     * Records that this file content has been imported.
     *
     * <p>
     * {@code MANDATORY} propagation is the safety rail: recording an import outside
     * the transaction that performed it would recreate the exact bug this class
     * exists to fix, so callers that forget to be transactional fail loudly at
     * development time instead of silently at 3am in a hospital.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordImport(String domain, String fileName, String checksum) {
        Query query = entityManager
                .createNativeQuery("INSERT INTO clinlims.config_import_log (domain, file_name, checksum, imported_at)"
                        + " VALUES (:domain, :fileName, :checksum, CURRENT_TIMESTAMP)"
                        + " ON CONFLICT (domain, file_name)"
                        + " DO UPDATE SET checksum = EXCLUDED.checksum, imported_at = EXCLUDED.imported_at");
        query.setParameter("domain", domain);
        query.setParameter("fileName", fileName);
        query.setParameter("checksum", checksum);
        query.executeUpdate();
    }
}
