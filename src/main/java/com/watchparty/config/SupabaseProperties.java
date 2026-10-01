package com.watchparty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * خصائص Supabase العامة.
 *
 * التعليق بالعربية: لا نضع أي قيمة افتراضية حساسة هنا؛ application.properties يقرأها من البيئة فقط.
 */
@ConfigurationProperties(prefix = "watchparty.supabase")
public record SupabaseProperties(
        String url,
        String anonKey,
        String jwtSecret
) {
}
