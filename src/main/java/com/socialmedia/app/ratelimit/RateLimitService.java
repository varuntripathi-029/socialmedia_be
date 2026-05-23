package com.socialmedia.app.ratelimit;

import com.socialmedia.app.model.User;
import com.socialmedia.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    /**
     * Checks if a request is allowed based on the rate limit key, limit, and window duration.
     * Implements a resilient fixed window rate limiting algorithm using atomic Redis operations.
     */
    public boolean isAllowed(String key, int limit, Duration window) {
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current == null) {
                return true; // Graceful fallback
            }

            if (current == 1) {
                redisTemplate.expire(key, window);
            }

            return current <= limit;
        } catch (Exception e) {
            log.warn("Redis operations failed inside RateLimitService. Falling back to allowing request. Key: {}", key, e);
            return true; // Fallback: allow requests when Redis is offline
        }
    }

    /**
     * Resolves and caches the user ID for an authenticated username/email.
     * Prevents database query overhead on every single API request.
     */
    public Long getUserIdFromUsername(String username) {
        String cacheKey = "user:id:map:" + username;
        try {
            Object cachedId = redisTemplate.opsForValue().get(cacheKey);
            if (cachedId != null) {
                return Long.parseLong(cachedId.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve cached user ID for {}. Falling back to DB.", username, e);
        }

        // DB Fallback
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);

        if (user == null) {
            return null;
        }

        try {
            // Cache username/email to user ID mapping for 1 hour
            redisTemplate.opsForValue().set(cacheKey, user.getId().toString(), Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to cache user ID for {} in Redis.", username, e);
        }

        return user.getId();
    }
}
