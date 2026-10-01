package com.watchparty.security;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

public record SupabaseUserPrincipal(
        UUID id,
        String role,
        String email,
        Map<String, Object> claims
) implements Principal {

    public static final String WEBSOCKET_ATTRIBUTE = "supabaseUser";

    @Override
    public String getName() {
        return id.toString();
    }
}
