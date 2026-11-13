package com.pageturn.book;

import java.util.Locale;

import com.pageturn.common.error.ApiException;

/**
 * ISBN handling: accepts "978-0-13-468599-1" or "0 13 468599 X", stores just the digits, and checks the
 * check digit so typos are caught.
 */
final class Isbn {

    private Isbn() {
    }

    /** @return the normalized ISBN (10 or 13 characters), or null when blank. */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (compact.matches("[0-9]{9}[0-9X]") && validIsbn10(compact)) {
            return compact;
        }
        if (compact.matches("[0-9]{13}") && validIsbn13(compact)) {
            return compact;
        }
        throw ApiException.badRequest("ISBN is not valid. Check the digits (10 or 13 digits).");
    }

    private static boolean validIsbn10(String isbn) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            int value = isbn.charAt(i) == 'X' ? 10 : isbn.charAt(i) - '0';
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }

    private static boolean validIsbn13(String isbn) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = isbn.charAt(i) - '0';
            sum += i % 2 == 0 ? digit : digit * 3;
        }
        return sum % 10 == 0;
    }
}
