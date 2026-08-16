/**
 * Defensive utility to strip 2-digit year prefix (YY) from auto-generated stored sample numbers
 * (e.g. "261608-0001" -> "1608-0001").
 * Leaves custom/alphanumeric and already-formatted numbers unchanged.
 */
export function displaySampleNumber(raw) {
  if (!raw) return raw;
  const str = String(raw).trim();
  if (/^\d{6}-\d{4}(-\d+)?$/.test(str)) {
    return str.slice(2);
  }
  return str;
}
