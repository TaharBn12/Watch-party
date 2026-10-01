package com.watchparty.security;

import com.watchparty.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserService {

    public UUID requireCurrentUserId(HttpServletRequest request) {
        return requireCurrentUser().id();
    }

    public SupabaseUserPrincipal requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("المصادقة مطلوبة عبر Supabase JWT");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            return supabaseUserPrincipal;
        }

        throw new UnauthorizedException("تعذر قراءة المستخدم الحالي من سياق الأمان");
    }
}
