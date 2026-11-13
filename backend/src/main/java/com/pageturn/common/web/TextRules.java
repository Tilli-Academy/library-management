package com.pageturn.common.web;

/**
 * Shared input rules (used in @Pattern annotations and when cleaning values before saving).
 *
 * <p>About XSS: text is stored exactly as typed (minus invisible control characters) and is made safe
 * when DISPLAYED — React escapes everything it renders, and the API only ever returns JSON. Encoding
 * HTML into the database instead would corrupt data (e.g. "Tom &amp; Jerry") and is not a real defence.
 */
public final class TextRules {

    /** Single-line text: no control characters at all (blocks NUL bytes, line breaks, terminal escapes). */
    public static final String SINGLE_LINE = "^[^\\p{Cntrl}]*$";

    /** Multi-line text: line breaks and tabs allowed, other control characters refused. */
    public static final String MULTI_LINE = "^[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*$";

    /** Phone number: digits, optional leading +, spaces, dashes, brackets. Same rule as the database. */
    public static final String PHONE = "^\\+?[0-9][0-9 ()-]{5,18}$";

    private TextRules() {
    }

    /**
     * Cleans multi-line text: unifies look-alike characters (Unicode NFKC, e.g. full-width letters), removes
     * invisible formatting characters (zero-width spaces, right-to-left overrides…), trims, and turns blank
     * text into null.
     */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .replaceAll("\\p{Cf}", "")
                .strip();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Cleans single-line text (titles, names): like {@link #clean}, and also turns any run of spaces
     * (including non-breaking ones) into one normal space. So "Dune", " Dune " and "Du\u200Bne" can't be
     * used to sneak past duplicate checks.
     */
    public static String cleanLine(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        String collapsed = cleaned.replaceAll("[\\s\\p{Z}]+", " ").strip();
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** For required fields: the cleaned value, or a clear error if nothing visible is left. */
    public static String requiredLine(String value, String message) {
        String cleaned = cleanLine(value);
        if (cleaned == null) {
            throw com.pageturn.common.error.ApiException.badRequest(message);
        }
        return cleaned;
    }
}
