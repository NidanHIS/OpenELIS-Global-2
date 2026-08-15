package org.openelisglobal.nidanresult;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Orchestration contract for the NIDAN result bridge.
 *
 * <p>
 * Implementations must be additive — they must not modify any existing service,
 * DAO, or entity. All result persistence is done by delegating to the same
 * pipeline ({@code ResultUtil} → {@code LogbookResultsPersistService}) that the
 * OpenELIS-Global-2 UI uses.
 */
public interface NidanResultService {

    /**
     * Returns all pending analyses for the given sample number.
     *
     * <p>
     * A "pending" analysis is one whose status is in:
     * <ul>
     * <li>NotStarted (test entered but not yet started)</li>
     * <li>TechnicalAcceptance (result entered, awaiting validation)</li>
     * </ul>
     * Cancelled analyses are always excluded.
     *
     * @param sampleNumber the NIDAN sample number (e.g. {@code 1508-0001})
     * @return list of pending result items; empty list if none found
     * @throws NidanResultException if the sample does not exist or a system error
     *                              occurs
     */
    List<NidanPendingResultItem> getPendingResults(String sampleNumber) throws NidanResultException;

    /**
     * Returns all active, orderable tests in the system.
     *
     * <p>
     * This endpoint allows callers to discover available tests before placing
     * orders or to validate test names when posting results.
     *
     * @param orderableOnly if true (default), returns only tests marked as
     *                      orderable; if false, returns all active tests
     * @return list of test items with id, name, and orderable status
     * @throws NidanResultException if a system error occurs
     */
    List<NidanTestItem> getAllTests(boolean orderableOnly) throws NidanResultException;

    /**
     * Persists a single result for a specific analysis, using the exact same
     * pipeline as the OpenELIS-Global-2 result-entry UI.
     *
     * <p>
     * The {@code sysUserId} must be the resolved OpenELIS system user ID of the
     * authenticated caller.
     *
     * <p>
     * The {@code httpRequest} is the live servlet request from the controller. It
     * is threaded through to
     * {@link org.openelisglobal.result.action.util.ResultUtil} which calls
     * {@code ControllerUtills.getSysUserId(request)} internally. For Basic Auth
     * requests the session lookup returns {@code null} and the method falls through
     * to the SecurityContext strategy — which succeeds because Spring Security has
     * already authenticated the principal on this request thread. Passing
     * {@code null} here would cause a {@code NullPointerException} inside
     * {@code ControllerUtills.getSysUserId} before the SecurityContext fallback is
     * reached.
     *
     * @param request     the validated, non-null inbound payload
     * @param sysUserId   the numeric system user ID (from the authenticated
     *                    session)
     * @param httpRequest the live {@link HttpServletRequest} from the controller
     * @return a populated success response
     * @throws NidanResultException if validation fails, the analysis is not found,
     *                              or a system error occurs
     */
    NidanResultResponse submitResult(NidanResultRequest request, String sysUserId, HttpServletRequest httpRequest)
            throws NidanResultException;
}
