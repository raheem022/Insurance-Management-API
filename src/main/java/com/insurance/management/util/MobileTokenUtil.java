package com.insurance.management.util;

import com.insurance.management.entity.User;
import com.insurance.management.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Mobile Token Utility - Handles mobile app token validation
 * Based on the Node.js decodeToken function
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MobileTokenUtil {

    private final UserService userService;

    /**
     * Decode and validate mobile authentication token
     * Matches the decodeToken function from Node.js server.js
     */
    public TokenData decodeToken(String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                log.warn("🔐 Invalid token format - missing Bearer prefix");
                return null;
            }
            
            String tokenData = token.replace("Bearer ", "");
            String decoded = new String(Base64.getDecoder().decode(tokenData), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            
            if (parts.length < 4) {
                log.warn("🔐 Invalid token format - insufficient parts: {}", parts.length);
                return null;
            }
            
            long userId = Long.parseLong(parts[0]);
            String username = parts[1];
            String state = parts[2];
            long timestamp = Long.parseLong(parts[3]);
            
            // Basic token validation (check if not too old - 24 hours)
            long tokenAge = System.currentTimeMillis() - timestamp;
            long maxAge = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
            
            if (tokenAge > maxAge) {
                log.warn("🔐 Token expired - age: {}ms, max: {}ms", tokenAge, maxAge);
                return null; // Token expired
            }
            
            TokenData tokenDataObj = new TokenData();
            tokenDataObj.setUserId(userId);
            tokenDataObj.setUsername(username);
            tokenDataObj.setState(state);
            tokenDataObj.setTimestamp(timestamp);
            
            log.debug("✅ Token decoded successfully for user: {} (ID: {})", username, userId);
            return tokenDataObj;
            
        } catch (Exception e) {
            log.warn("🔐 Token decoding failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract token from Authorization header
     */
    public String extractTokenFromRequest(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }

    /**
     * Validate token and return user if valid
     */
    public User validateTokenAndGetUser(HttpServletRequest request) {
        String authHeader = extractTokenFromRequest(request);
        TokenData tokenData = decodeToken(authHeader);
        
        if (tokenData == null) {
            return null;
        }
        
        // Verify user exists and is active
        Optional<User> userOptional = userService.findById(tokenData.getUserId());
        if (userOptional.isEmpty()) {
            log.warn("🔐 Token validation failed - User not found: {}", tokenData.getUserId());
            return null;
        }
        
        User user = userOptional.get();
        if (!user.isAccountActive()) {
            log.warn("🔐 Token validation failed - Account inactive: {}", user.getUsername());
            return null;
        }
        
        // Verify username matches (security check)
        if (!user.getUsername().equals(tokenData.getUsername())) {
            log.warn("🔐 Token validation failed - Username mismatch: expected {}, got {}", 
                    user.getUsername(), tokenData.getUsername());
            return null;
        }
        
        log.debug("✅ Token validation successful for user: {}", user.getUsername());
        return user;
    }

    /**
     * Generate mobile authentication token
     * Matches the token generation logic from Node.js (if needed for login endpoints)
     */
    public String generateToken(User user) {
        long timestamp = System.currentTimeMillis();
        String tokenString = String.format("%d:%s:%s:%d", 
                user.getId(), 
                user.getUsername(),
                user.getLocationState() != null ? user.getLocationState() : "",
                timestamp);
        
        String encoded = Base64.getEncoder().encodeToString(tokenString.getBytes(StandardCharsets.UTF_8));
        String token = "Bearer " + encoded;
        
        log.info("🎫 Generated mobile token for user: {} (expires in 24h)", user.getUsername());
        return token;
    }

    /**
     * Token Data class for holding decoded token information
     */
    public static class TokenData {
        private Long userId;
        private String username;
        private String state;
        private Long timestamp;

        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }
}
