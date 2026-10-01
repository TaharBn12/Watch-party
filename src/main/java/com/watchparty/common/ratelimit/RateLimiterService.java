package com.watchparty.common.ratelimit;

import com.watchparty.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimiterService {

    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public void check(String key, int maxRequests, Duration window, String message) {
        if (maxRequests <= 0 || window == null || window.isNegative() || window.isZero()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now, 0));

        synchronized (counter) {
            if (now - counter.windowStartedAt >= windowMs) {
                counter.windowStartedAt = now;
                counter.count = 0;
            }

            counter.count++;
            if (counter.count > maxRequests) {
                throw new ForbiddenException(message);
            }
        }

        cleanup(now, windowMs);
    }

    private void cleanup(long now, long windowMs) {
        if (counters.size() < 1024) {
            return;
        }

        for (Map.Entry<String, WindowCounter> entry : counters.entrySet()) {
            WindowCounter counter = entry.getValue();
            if (now - counter.windowStartedAt > windowMs * 4) {
                counters.remove(entry.getKey(), counter);
            }
        }
    }

    private static final class WindowCounter {
        private long windowStartedAt;
        private int count;

        private WindowCounter(long windowStartedAt, int count) {
            this.windowStartedAt = windowStartedAt;
            this.count = count;
        }
    }
}
