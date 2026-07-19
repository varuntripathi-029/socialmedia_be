package com.socialmedia.app.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<RateLimitRule> rules = new ArrayList<>();

    // Initialize the configured rate limit rules
    {
        rules.add(new RateLimitRule("POST", "/api/auth/login", "/api/auth/login", 5, Duration.ofMinutes(1), false));
        rules.add(new RateLimitRule("POST", "/api/auth/signup", "/api/auth/signup", 5, Duration.ofMinutes(1), false));
        rules.add(new RateLimitRule("POST", "/api/posts", "/api/posts", 10, Duration.ofHours(1), true));
        rules.add(new RateLimitRule("POST", "/api/events", "/api/events", 5, Duration.ofDays(1), true));
        rules.add(new RateLimitRule("POST", "/api/events/*/join", "/api/events/{id}/join", 20, Duration.ofHours(1), true));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        // Check if any rule matches the incoming request
        RateLimitRule matchedRule = null;
        for (RateLimitRule rule : rules) {
            if (rule.method.equalsIgnoreCase(method) && pathMatcher.match(rule.pathPattern, path)) {
                matchedRule = rule;
                break;
            }
        }

        if (matchedRule != null) {
            log.debug("RateLimit rule matched for {} {}. Rule limit: {} per {}", method, path, matchedRule.limit, matchedRule.window);
            
            // Extract IP Address. X-Forwarded-For is appended to by each proxy hop, with the
            // furthest-right entry being the IP the outermost trusted proxy actually observed.
            // The app sits behind exactly one trusted proxy (the hosting platform's edge), so we
            // trust that last entry — never the first, which is fully client-controlled and lets
            // a client spoof any IP it wants by sending its own X-Forwarded-For header.
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            } else {
                String[] hops = ip.split(",");
                ip = hops[hops.length - 1].trim();
            }

            // Check security authentication context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAuthenticated = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());

            String redisKey;
            if (matchedRule.perUser && isAuthenticated) {
                String username = auth.getName();
                Long userId = rateLimitService.getUserIdFromUsername(username);
                if (userId != null) {
                    redisKey = "rate:" + method + ":" + matchedRule.normalizedPath + ":user:" + userId;
                } else {
                    // Fallback to IP if userId cannot be resolved
                    redisKey = "rate:" + method + ":" + matchedRule.normalizedPath + ":ip:" + ip;
                }
            } else {
                redisKey = "rate:" + method + ":" + matchedRule.normalizedPath + ":ip:" + ip;
            }

            try {
                boolean allowed = rateLimitService.isAllowed(redisKey, matchedRule.limit, matchedRule.window);
                if (!allowed) {
                    throw new RateLimitExceededException("Too many requests. Try again later.");
                }
            } catch (RateLimitExceededException ex) {
                log.warn("Rate limit breached for key: {}. Responding with 429.", redisKey);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"message\": \"" + ex.getMessage() + "\"}");
                return; // Cease execution, do not proceed along the filter chain
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class RateLimitRule {
        private final String method;
        private final String pathPattern;
        private final String normalizedPath;
        private final int limit;
        private final Duration window;
        private final boolean perUser;

        public RateLimitRule(String method, String pathPattern, String normalizedPath, int limit, Duration window, boolean perUser) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.normalizedPath = normalizedPath;
            this.limit = limit;
            this.window = window;
            this.perUser = perUser;
        }
    }
}
