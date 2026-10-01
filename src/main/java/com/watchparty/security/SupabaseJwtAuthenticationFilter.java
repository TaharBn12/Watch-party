package com.watchparty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class SupabaseJwtAuthenticationFilter extends OncePerRequestFilter {

    private final SupabaseJwtService supabaseJwtService;
    private final ObjectMapper objectMapper;

    public SupabaseJwtAuthenticationFilter(SupabaseJwtService supabaseJwtService, ObjectMapper objectMapper) {
        this.supabaseJwtService = supabaseJwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorizationHeader)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = supabaseJwtService.extractBearerToken(authorizationHeader);
            SupabaseUserPrincipal principal = supabaseJwtService.verify(token);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    authoritiesFor(principal)
            );
            authentication.setDetails(principal.claims());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (SupabaseJwtAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, request.getRequestURI(), exception.getMessage());
        }
    }

    private List<SimpleGrantedAuthority> authoritiesFor(SupabaseUserPrincipal principal) {
        String role = StringUtils.hasText(principal.role()) ? principal.role() : "authenticated";
        String normalizedRole = "ROLE_" + role.toUpperCase(Locale.ROOT).replace('-', '_');
        return List.of(new SimpleGrantedAuthority(normalizedRole));
    }

    private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse error = ErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized",
                message,
                path
        );
        objectMapper.writeValue(response.getWriter(), error);
    }
}
