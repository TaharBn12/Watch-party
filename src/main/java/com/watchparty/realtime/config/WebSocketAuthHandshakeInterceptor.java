package com.watchparty.realtime.config;

import com.watchparty.security.SupabaseJwtAuthenticationException;
import com.watchparty.security.SupabaseJwtService;
import com.watchparty.security.SupabaseUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final SupabaseJwtService supabaseJwtService;

    public WebSocketAuthHandshakeInterceptor(SupabaseJwtService supabaseJwtService) {
        this.supabaseJwtService = supabaseJwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            String token = resolveToken(request);
            if (!StringUtils.hasText(token)) {
                // التعليق بالعربية: نسمح بفتح الاتصال حتى يستطيع العميل إرسال JWT داخل STOMP CONNECT headers.
                // إذا لم يرسل الرمز في CONNECT سيمنعه WebSocketAuthChannelInterceptor فورًا.
                return true;
            }
            SupabaseUserPrincipal principal = supabaseJwtService.verify(token);
            attributes.put(SupabaseUserPrincipal.WEBSOCKET_ATTRIBUTE, principal);
            return true;
        } catch (SupabaseJwtAuthenticationException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // لا يوجد تنظيف مطلوب بعد المصافحة.
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorizationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorizationHeader)) {
            return supabaseJwtService.extractBearerToken(authorizationHeader);
        }

        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();
        String accessToken = queryParams.getFirst("access_token");
        if (StringUtils.hasText(accessToken)) {
            return accessToken.trim();
        }

        String token = queryParams.getFirst("token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }

        return null;
    }
}
