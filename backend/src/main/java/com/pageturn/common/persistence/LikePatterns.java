package com.pageturn.common.persistence;

import java.util.Locale;

/**
 * Builds safe "contains" patterns for SQL LIKE searches.
 *
 * <p>The search text is always passed as a query PARAMETER (never glued into the SQL), so SQL injection
 * is impossible. This class additionally escapes the LIKE wildcards % and _ so that searching for "50%"
 * finds "50%" instead of matching everything.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** @return a lower-case {@code %text%} pattern; {@code %} (match everything) when the text is blank. */
    public static String contains(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        String escaped = text.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
