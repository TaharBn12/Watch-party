package com.watchparty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.config.SupabaseProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupabaseJwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-long-enough";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SupabaseJwtService jwtService = new SupabaseJwtService(
            new SupabaseProperties("https://example.supabase.co", "anon", SECRET),
            objectMapper
    );

    @Test
    void verifiesValidHs256Token() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = token(Map.of(
                "sub", userId.toString(),
                "role", "authenticated",
                "email", "user@example.com",
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));

        SupabaseUserPrincipal principal = jwtService.verify(token);

        assertEquals(userId, principal.id());
        assertEquals("authenticated", principal.role());
        assertEquals("user@example.com", principal.email());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = token(Map.of(
                "sub", UUID.randomUUID().toString(),
                "exp", Instant.now().minusSeconds(3600).getEpochSecond()
        ));

        assertThrows(SupabaseJwtAuthenticationException.class, () -> jwtService.verify(token));
    }

    private String token(Map<String, Object> claims) throws Exception {
        String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
        String payload = base64Url(objectMapper.writeValueAsBytes(claims));
        String signed = header + "." + payload;
        return signed + "." + base64Url(hmacSha256(signed));
    }

    private byte[] hmacSha256(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
