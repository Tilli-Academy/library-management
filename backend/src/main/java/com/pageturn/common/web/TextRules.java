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

    private TextRules() {
    }

    /** Trims, and turns blank text into null. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
