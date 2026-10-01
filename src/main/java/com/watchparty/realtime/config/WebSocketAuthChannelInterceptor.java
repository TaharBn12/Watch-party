package com.watchparty.realtime.config;

import com.watchparty.room.service.RoomPermissionService;
import com.watchparty.security.SupabaseJwtAuthenticationException;
import com.watchparty.security.SupabaseJwtService;
import com.watchparty.security.SupabaseUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";

    private final SupabaseJwtService supabaseJwtService;
    private final RoomPermissionService roomPermissionService;

    public WebSocketAuthChannelInterceptor(SupabaseJwtService supabaseJwtService, RoomPermissionService roomPermissionService) {
        this.supabaseJwtService = supabaseJwtService;
        this.roomPermissionService = roomPermissionService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            SupabaseUserPrincipal principal = resolvePrincipal(accessor);
            accessor.setUser(principal);
            return message;
        }

        if (requiresAuthenticatedUser(accessor.getCommand()) && accessor.getUser() == null) {
            throw new AccessDeniedException("WebSocket message يتطلب مصادقة Supabase JWT");
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        SupabaseUserPrincipal user = requirePrincipal(accessor.getUser());
        Optional<UUID> roomId = extractRoomIdFromTopic(accessor.getDestination());
        roomId.ifPresent(id -> roomPermissionService.requireActiveMember(id, user.id()));
    }

    private Optional<UUID> extractRoomIdFromTopic(String destination) {
        if (!StringUtils.hasText(destination) || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return Optional.empty();
        }

        String rest = destination.substring(ROOM_TOPIC_PREFIX.length());
        int slashIndex = rest.indexOf('/');
        String rawRoomId = slashIndex >= 0 ? rest.substring(0, slashIndex) : rest;
        try {
            return Optional.of(UUID.fromString(rawRoomId));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("وجهة الاشتراك تحتوي roomId غير صالح");
        }
    }

    private SupabaseUserPrincipal resolvePrincipal(StompHeaderAccessor accessor) {
        String authorizationHeader = firstNativeHeader(accessor, HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorizationHeader)) {
            authorizationHeader = firstNativeHeader(accessor, "authorization");
        }
        if (StringUtils.hasText(authorizationHeader)) {
            String token = supabaseJwtService.extractBearerToken(authorizationHeader);
            return supabaseJwtService.verify(token);
        }

        String accessToken = firstNativeHeader(accessor, "access_token");
        if (StringUtils.hasText(accessToken)) {
            return supabaseJwtService.verify(accessToken.trim());
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object principal = sessionAttributes.get(SupabaseUserPrincipal.WEBSOCKET_ATTRIBUTE);
            if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
                return supabaseUserPrincipal;
            }
        }

        Principal existingUser = accessor.getUser();
        if (existingUser instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            return supabaseUserPrincipal;
        }

        throw new SupabaseJwtAuthenticationException("STOMP CONNECT يتطلب JWT صالحًا");
    }

    private SupabaseUserPrincipal requirePrincipal(Principal principal) {
        if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            return supabaseUserPrincipal;
        }
        throw new AccessDeniedException("WebSocket subscription يتطلب مصادقة Supabase JWT");
    }

    private boolean requiresAuthenticatedUser(StompCommand command) {
        return command == StompCommand.SEND
                || command == StompCommand.SUBSCRIBE
                || command == StompCommand.UNSUBSCRIBE
                || command == StompCommand.DISCONNECT;
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
