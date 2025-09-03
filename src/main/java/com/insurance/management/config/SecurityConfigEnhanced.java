package com.insurance.management.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced Security Configuration
 * Implements security headers, CORS, and rate limiting similar to Node.js middleware
 */
@Configuration
public class SecurityConfigEnhanced {
    
    // Rate limiting storage
    private final ConcurrentHashMap<String, RateLimitData> rateLimitMap = new ConcurrentHashMap<>();
    
    /**
     * CORS configuration matching Node.js setup
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow all origins in development, specific origins in production
        String env = System.getProperty("spring.profiles.active", "development");
        if ("production".equals(env)) {
            // Production CORS origins
            config.setAllowedOriginPatterns(Arrays.asList(
                "https://*.azurewebsites.net",
                "http://localhost:*",
                "http://127.0.0.1:*"
            ));
        } else {
            // Development - allow all origins
            config.addAllowedOrigin("*");
        }
        
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With"));
        config.setAllowCredentials(!config.getAllowedOrigins().contains("*")); // Only if not allowing all origins
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        
        return bean;
    }
    
    /**
     * Security Headers Filter
     * Implements security headers similar to Node.js helmet middleware
     */
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SecurityHeadersFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(2);
        return registrationBean;
    }
    
    /**
     * Rate Limiting Filter
     * Implements rate limiting similar to Node.js express-rate-limit
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RateLimitFilter());
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(3);
        return registrationBean;
    }
    
    /**
     * Request Timeout Filter
     * Prevents hanging requests similar to Node.js timeout middleware
     */
    @Bean
    public FilterRegistrationBean<RequestTimeoutFilter> requestTimeoutFilter() {
        FilterRegistrationBean<RequestTimeoutFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RequestTimeoutFilter());
        registrationBean.addUrlPatterns("/api/*");
        registrationBean.setOrder(4);
        return registrationBean;
    }
    
    // Filter implementations
    
    public static class SecurityHeadersFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Security headers similar to Node.js helmet
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
            httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // Production-specific headers
            String env = System.getProperty("spring.profiles.active", "development");
            if ("production".equals(env)) {
                httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
            
            chain.doFilter(request, response);
        }
    }
    
    public class RateLimitFilter implements Filter {
        private static final int GENERAL_LIMIT = 200; // requests per window
        private static final int AUTH_LIMIT = 10; // login attempts per window
        private static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes
        
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            String clientIp = getClientIpAddress(httpRequest);
            String path = httpRequest.getRequestURI();
            
            // Skip rate limiting for health checks
            if (path.equals("/health") || path.equals("/")) {
                chain.doFilter(request, response);
                return;
            }
            
            // Determine rate limit based on path
            int limit = path.contains("/auth/") ? AUTH_LIMIT : GENERAL_LIMIT;
            
            if (!checkRateLimit(clientIp, limit)) {
                httpResponse.setStatus(429); // Too Many Requests
                httpResponse.setHeader("Content-Type", "application/json");
                httpResponse.getWriter().write("{\"success\":false,\"error\":\"Too many requests, please try again later\"}");
                return;
            }
            
            chain.doFilter(request, response);
        }
        
        private boolean checkRateLimit(String clientIp, int limit) {
            long now = System.currentTimeMillis();
            
            rateLimitMap.compute(clientIp, (key, data) -> {
                if (data == null || (now - data.windowStart) > WINDOW_MS) {
                    return new RateLimitData(now, new AtomicInteger(1));
                } else {
                    data.count.incrementAndGet();
                    return data;
                }
            });
            
            RateLimitData data = rateLimitMap.get(clientIp);
            return data.count.get() <= limit;
        }
        
        private String getClientIpAddress(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        }
    }
    
    public static class RequestTimeoutFilter implements Filter {
        private static final long TIMEOUT_MS = 30000; // 30 seconds
        
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            
            // Create timeout thread
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(TIMEOUT_MS);
                    if (!httpResponse.isCommitted()) {
                        httpResponse.setStatus(408); // Request Timeout
                        httpResponse.setHeader("Content-Type", "application/json");
                        try {
                            httpResponse.getWriter().write("{\"success\":false,\"error\":\"Request timeout - please try again\"}");
                        } catch (IOException e) {
                            // Response may already be committed
                        }
                    }
                } catch (InterruptedException e) {
                    // Request completed normally
                    Thread.currentThread().interrupt();
                }
            });
            
            timeoutThread.start();
            
            try {
                chain.doFilter(request, response);
            } finally {
                timeoutThread.interrupt();
            }
        }
    }
    
    // Rate limit data structure
    private static class RateLimitData {
        final long windowStart;
        final AtomicInteger count;
        
        RateLimitData(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
