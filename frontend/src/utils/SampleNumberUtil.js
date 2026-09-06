/**
 * NIDAN Sample Number utility — frontend mirror of Java SampleNumberUtil.
 *
 * Stored format (AUTO): YYMMDDxxxx — 10 all-digit chars (e.g. "2609060001").
 * Display format:       DDxxxx     — 6 all-digit chars  (e.g. "060001").
 * Manual entries:       any alphanumeric, stored verbatim, never transformed.
 *
 * The REST generate endpoint already returns display format (DDxxxx).
 * This utility is used to strip the prefix if a stored value ever reaches the UI.
 */

/**
 * Converts a stored AUTO sample number to display format by stripping the 4-digit YYMM prefix.
 * Passes through any non-10-digit string unchanged (manual entries, display-format, null).
 *
 * Examples:
 *   "2609060001" → "060001"  (stored AUTO → display)
 *   "060001"     → "060001"  (already display, pass-through)
 *   "LAB01"      → "LAB01"   (manual, pass-through)
 *   null/""      → unchanged
 *
 * @param {string|null} raw - value as stored in DB or returned from API
 * @returns {string} display form
 */
export function displaySampleNumber(raw) {
  if (!raw) return raw;
  const s = String(raw).trim();
  // Exactly 10 digits = stored AUTO format → strip first 4 chars (YYMM)
  if (/^\d{10}$/.test(s)) {
    return s.slice(4);
  }
  return s;
}

/**
 * Returns true if the value is in display AUTO format (exactly 6 digits).
 * @param {string|null} s
 * @returns {boolean}
 */
export function isDisplayFormat(s) {
  if (!s) return false;
  return /^\d{6}$/.test(String(s).trim());
}

/**
 * Returns true if the value is in stored AUTO format (exactly 10 digits).
 * @param {string|null} s
 * @returns {boolean}
 */
export function isStoredAutoFormat(s) {
  if (!s) return false;
  return /^\d{10}$/.test(String(s).trim());
}
