package com.youmo.core.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        AttemptRecord r = attempts.get(normalize(key));
        if (r == null) return false;
        if (r.lockedUntil != null && Instant.now().isBefore(r.lockedUntil)) return true;
        // lock expired — reset
        if (r.lockedUntil != null && Instant.now().isAfter(r.lockedUntil)) {
            attempts.remove(normalize(key));
        }
        return false;
    }

    public long remainingSeconds(String key) {
        AttemptRecord r = attempts.get(normalize(key));
        if (r == null || r.lockedUntil == null) return 0;
        long secs = r.lockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, secs);
    }

    public int remainingAttempts(String key) {
        AttemptRecord r = attempts.get(normalize(key));
        if (r == null) return MAX_ATTEMPTS;
        return Math.max(0, MAX_ATTEMPTS - r.count);
    }

    public void recordFailure(String key) {
        String k = normalize(key);
        AttemptRecord r = attempts.computeIfAbsent(k, _k -> new AttemptRecord());
        r.count++;
        if (r.count >= MAX_ATTEMPTS) {
            r.lockedUntil = Instant.now().plusSeconds(LOCK_MINUTES * 60);
        }
    }

    public void reset(String key) {
        attempts.remove(normalize(key));
    }

    private String normalize(String key) {
        return key == null ? "" : key.strip().toLowerCase();
    }

    private static class AttemptRecord {
        int count;
        Instant lockedUntil;
    }
}
