package com.watchparty.security.filter;

import com.watchparty.config.CorsConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Component
public class UnsafeRequestOriginFilter extends OncePerRequestFilter {

    private final List<String> allowedOriginPatterns;

    public UnsafeRequestOriginFilter(@Value("${watchparty.cors.allowed-origins:http://localhost:8080}") String allowedOrigins) {
        this.allowedOriginPatterns = CorsConfig.parseCsv(allowedOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || isSafeMethod(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isSameOrigin(request, origin) || isAllowedOrigin(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"Origin غير مسموح لهذا الطلب\"}");
    }

    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isSameOrigin(HttpServletRequest request, String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = request.getScheme();
            String host = request.getServerName();
            int port = request.getServerPort();
            int originPort = uri.getPort() == -1 ? defaultPort(uri.getScheme()) : uri.getPort();
            return scheme.equalsIgnoreCase(uri.getScheme())
                    && host.equalsIgnoreCase(uri.getHost())
                    && port == originPort;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private boolean isAllowedOrigin(String origin) {
        return allowedOriginPatterns.stream().anyMatch(pattern -> matchesOriginPattern(pattern, origin));
    }

    private boolean matchesOriginPattern(String pattern, String origin) {
        if ("*".equals(pattern) || pattern.equalsIgnoreCase(origin)) {
            return true;
        }
        if (!pattern.contains("*")) {
            return false;
        }
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
        return origin.matches(regex);
    }
}
