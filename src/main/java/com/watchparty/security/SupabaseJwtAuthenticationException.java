package com.watchparty.security;

import org.springframework.security.core.AuthenticationException;

public class SupabaseJwtAuthenticationException extends AuthenticationException {

    public SupabaseJwtAuthenticationException(String message) {
        super(message);
    }

    public SupabaseJwtAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
