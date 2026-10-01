package com.watchparty.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "frame-ancestors 'self'",
            "script-src 'self' https://cdn.jsdelivr.net https://www.youtube.com https://s.ytimg.com",
            "style-src 'self'",
            "img-src 'self' data: https:",
            "font-src 'self' data:",
            "connect-src 'self' https://*.supabase.co wss: ws:",
            "media-src 'self' blob: https:",
            "frame-src 'self' https://www.youtube.com https://www.youtube-nocookie.com",
            "worker-src 'self' blob:",
            "form-action 'self'"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        setIfMissing(response, "Content-Security-Policy", CONTENT_SECURITY_POLICY);
        setIfMissing(response, "X-Content-Type-Options", "nosniff");
        setIfMissing(response, "X-Frame-Options", "SAMEORIGIN");
        setIfMissing(response, "Referrer-Policy", "strict-origin-when-cross-origin");
        setIfMissing(response, "Permissions-Policy", "geolocation=(), payment=(), camera=(self), microphone=(self), fullscreen=(self)");
        setIfMissing(response, "Cross-Origin-Opener-Policy", "same-origin-allow-popups");

        filterChain.doFilter(request, response);
    }

    private void setIfMissing(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
