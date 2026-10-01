package com.watchparty.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.config.SupabaseProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseJwtService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SupabaseProperties supabaseProperties;
    private final ObjectMapper objectMapper;

    public SupabaseJwtService(SupabaseProperties supabaseProperties, ObjectMapper objectMapper) {
        this.supabaseProperties = supabaseProperties;
        this.objectMapper = objectMapper;
    }

    public SupabaseUserPrincipal verify(String token) {
        if (!StringUtils.hasText(supabaseProperties.jwtSecret())) {
            throw new SupabaseJwtAuthenticationException("SUPABASE_JWT_SECRET غير مضبوط في متغيرات البيئة");
        }
        if (!StringUtils.hasText(token)) {
            throw new SupabaseJwtAuthenticationException("رمز المصادقة غير موجود");
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new SupabaseJwtAuthenticationException("صيغة JWT غير صحيحة");
        }

        Map<String, Object> header = decodeJsonPart(parts[0], "header");
        String algorithm = asString(header.get("alg"));
        if (!"HS256".equals(algorithm)) {
            throw new SupabaseJwtAuthenticationException("خوارزمية JWT غير مدعومة: " + algorithm);
        }

        verifySignature(token, parts);

        Map<String, Object> claims = decodeJsonPart(parts[1], "payload");
        validateTimeClaims(claims);

        UUID userId = readSubject(claims);
        String role = asString(claims.get("role"));
        String email = asString(claims.get("email"));

        return new SupabaseUserPrincipal(
                userId,
                StringUtils.hasText(role) ? role : "authenticated",
                email,
                Collections.unmodifiableMap(claims)
        );
    }

    public String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }

        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new SupabaseJwtAuthenticationException("Authorization header يجب أن يبدأ بـ Bearer");
        }

        String token = trimmed.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            throw new SupabaseJwtAuthenticationException("Bearer token فارغ");
        }
        return token;
    }

    private void verifySignature(String token, String[] parts) {
        try {
            String signedContent = parts[0] + "." + parts[1];
            byte[] expectedSignature = hmacSha256(signedContent, supabaseProperties.jwtSecret());
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);

            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new SupabaseJwtAuthenticationException("توقيع JWT غير صحيح");
            }
        } catch (IllegalArgumentException exception) {
            throw new SupabaseJwtAuthenticationException("توقيع JWT غير قابل للقراءة", exception);
        }
    }

    private byte[] hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new SupabaseJwtAuthenticationException("تعذر التحقق من توقيع JWT", exception);
        }
    }

    private Map<String, Object> decodeJsonPart(String base64UrlPart, String partName) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(base64UrlPart);
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw new SupabaseJwtAuthenticationException("تعذر قراءة " + partName + " داخل JWT", exception);
        }
    }

    private void validateTimeClaims(Map<String, Object> claims) {
        long now = Instant.now().getEpochSecond();
        Long expiresAt = asLong(claims.get("exp"));
        if (expiresAt == null) {
            throw new SupabaseJwtAuthenticationException("JWT لا يحتوي exp");
        }
        if (expiresAt + CLOCK_SKEW_SECONDS < now) {
            throw new SupabaseJwtAuthenticationException("JWT منتهي الصلاحية");
        }

        Long notBefore = asLong(claims.get("nbf"));
        if (notBefore != null && notBefore - CLOCK_SKEW_SECONDS > now) {
            throw new SupabaseJwtAuthenticationException("JWT غير صالح بعد");
        }
    }

    private UUID readSubject(Map<String, Object> claims) {
        String subject = asString(claims.get("sub"));
        if (!StringUtils.hasText(subject)) {
            throw new SupabaseJwtAuthenticationException("JWT لا يحتوي sub للمستخدم");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new SupabaseJwtAuthenticationException("قيمة sub في JWT ليست UUID صالحًا", exception);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
