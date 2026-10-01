package com.watchparty.common.ratelimit;

import com.watchparty.common.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterServiceTest {

    @Test
    void blocksRequestsOverLimit() {
        RateLimiterService service = new RateLimiterService();
        assertDoesNotThrow(() -> service.check("chat:user", 2, Duration.ofMinutes(1), "too many"));
        assertDoesNotThrow(() -> service.check("chat:user", 2, Duration.ofMinutes(1), "too many"));
        assertThrows(ForbiddenException.class,
                () -> service.check("chat:user", 2, Duration.ofMinutes(1), "too many"));
    }
}
