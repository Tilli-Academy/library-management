package com.pageturn.common.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects oversized request bodies before anything parses them (protects memory/CPU, including on the
 * public sign-in and sign-up endpoints). Runs before Spring Security.
 * <ul>
 * <li>Declared Content-Length over the limit → 413 immediately.</li>
 * <li>Bodies without a length (chunked) are cut off at the limit while being read → 400.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    /** The only endpoint that accepts big bodies: uploading a book cover picture. */
    private static final java.util.regex.Pattern COVER_UPLOAD =
            java.util.regex.Pattern.compile("^/api/v1/admin/books/[0-9]+/cover$");

    private final long defaultMaxBytes;
    private final long coverMaxBytes;

    public RequestSizeLimitFilter(@Value("${pageturn.http.max-request-bytes:65536}") long defaultMaxBytes,
            @Value("${pageturn.http.max-cover-upload-bytes:3407872}") long coverMaxBytes) {
        this.defaultMaxBytes = defaultMaxBytes;
        this.coverMaxBytes = coverMaxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long maxBytes = COVER_UPLOAD.matcher(com.pageturn.common.web.RequestPaths.of(request)).matches()
                ? coverMaxBytes : defaultMaxBytes;
        if (request.getContentLengthLong() > maxBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Content Too Large","status":413,"detail":"The request is too large."}""");
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long count;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            count += n;
            if (count > maxBytes) {
                throw new IOException("Request body exceeds " + maxBytes + " bytes");
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }

}
