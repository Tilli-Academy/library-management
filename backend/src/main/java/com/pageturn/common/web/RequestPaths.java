package com.pageturn.common.web;

import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The request path exactly as Spring routes it: percent-decoded ("/%61pi" → "/api"), without the context
 * path, without ";jsessionid=..." parts and with double slashes collapsed.
 *
 * <p>Any filter that decides something from the URL must use this, never the raw getRequestURI() —
 * otherwise "/%61pi/v1/auth/login" would reach the login endpoint while skipping the check.
 */
public final class RequestPaths {

    private static final UrlPathHelper HELPER = new UrlPathHelper();

    static {
        HELPER.setUrlDecode(true);
        HELPER.setRemoveSemicolonContent(true);
    }

    private RequestPaths() {
    }

    public static String of(HttpServletRequest request) {
        return HELPER.getPathWithinApplication(request);
    }
}
