package com.insurance.management.service;

import com.insurance.management.dto.LoginRequest;
import com.insurance.management.dto.LoginResponse;
import com.insurance.management.entity.User;
import com.insurance.management.util.MobileTokenUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Arrays;
import java.util.List;

/**
 * Authentication Service handling both web and mobile login
 * Replicates the Node.js authentication logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthenticationService {
    
    private final UserService userService;
    private final MobileTokenUtil mobileTokenUtil;
    
    // Dynamic state validation - states should be loaded from configuration or database
    // No hardcoded state list
    
    /**
     * Authenticate user for web/admin login
     * Matches POST /api/auth/login from Node.js
     */
    public LoginResponse authenticateWebUser(LoginRequest request) {
        log.info("🌐 Web authentication attempt for username: {}", request.getUsername());
        
        try {
            // Authenticate using UserService
            Optional<User> userOpt = userService.authenticateUser(request.getUsername(), request.getPin());
            
            if (userOpt.isEmpty()) {
                log.warn("❌ Web authentication failed for username: {}", request.getUsername());
                return LoginResponse.builder()
                    .success(false)
                    .error("Invalid credentials or account locked")
                    .build();
            }
            
            User user = userOpt.get();
            
            // Update last login
            userService.updateLastLogin(user, "web");
            
            // Generate simple token (matching Node.js format)
            String token = generateWebToken(user);
            
            log.info("✅ Web authentication successful for user: {} ({})", 
                user.getUsername(), user.getUserRole());
            
            return LoginResponse.builder()
                .success(true)
                .data(LoginResponse.LoginData.builder()
                    .user(buildUserInfo(user))
                    .token(token)
                    .build())
                .build();
                
        } catch (Exception e) {
            log.error("❌ Web authentication error for username: {}", request.getUsername(), e);
            return LoginResponse.builder()
                .success(false)
                .error("Login failed")
                .build();
        }
    }
    
    /**
     * Authenticate mobile user with automatic state discovery when state not provided
     * Matches POST /api/auth/mobile-login from Node.js with enhanced discovery
     */
    public LoginResponse authenticateMobileUser(LoginRequest request) {
        log.info("📱 Mobile authentication attempt - Username: {}, State: {}", 
            request.getUsername(), request.getState());
        
        try {
            // Check if state discovery is needed (state not provided or empty)
            if (request.getState() == null || request.getState().trim().isEmpty()) {
                log.info("🔍 No state provided, starting automatic state discovery for: {}", request.getUsername());
                return authenticateMobileUserWithDiscovery(request);
            }
            
            // State provided - use existing logic
            return authenticateMobileUserWithState(request);
                
        } catch (Exception e) {
            log.error("❌ Mobile authentication error for username: {} in state: {}", 
                request.getUsername(), request.getState(), e);
            return LoginResponse.builder()
                .success(false)
                .error("Mobile login failed")
                .build();
        }
    }
    
    /**
     * Authenticate mobile user with automatic state discovery
     */
    private LoginResponse authenticateMobileUserWithDiscovery(LoginRequest request) {
        log.info("🔍 Starting mobile state discovery for username: {}", request.getUsername());
        
        // First authenticate the user to get their profile
        Optional<User> userOpt = userService.authenticateUser(request.getUsername(), request.getPin());
        
        if (userOpt.isEmpty()) {
            log.warn("❌ Mobile authentication failed during discovery for username: {}", request.getUsername());
            return LoginResponse.builder()
                .success(false)
                .error("Invalid credentials")
                .build();
        }
        
        User user = userOpt.get();
        String userState = user.getLocationState();
        
        // If user has no state, use default
        if (userState == null || userState.trim().isEmpty()) {
            log.warn("⚠️ User {} has no state information, using default: Karnataka", request.getUsername());
            userState = "Karnataka";
        }
        
        // Convert user's database state to backend state format if needed
        String backendState = convertToBackendStateFormat(userState);
        
        log.info("✅ State discovered for {}: {} -> {}", request.getUsername(), userState, backendState);
        
        // Update last login
        userService.updateLastLogin(user, "mobile");
        
        // Generate mobile session token with discovered state
        String token = generateMobileToken(user, backendState);
        
        log.info("✅ Mobile discovery authentication successful for: {} ({}) in discovered state: {}", 
            user.getUsername(), user.getUserRole(), backendState);
        
        return LoginResponse.builder()
            .success(true)
            .data(LoginResponse.LoginData.builder()
                .user(buildUserInfo(user))
                .token(token)
                .authenticatedFrom(getStateDatabaseName(backendState))
                .build())
            .build();
    }
    
    /**
     * Authenticate mobile user with specific state (original logic)
     */
    private LoginResponse authenticateMobileUserWithState(LoginRequest request) {
        // Skip hardcoded state validation - let authentication process handle it
        
        // Authenticate user
        Optional<User> userOpt = userService.authenticateUser(request.getUsername(), request.getPin());
        
        if (userOpt.isEmpty()) {
            log.warn("❌ Mobile authentication failed for username: {} in state: {}", 
                request.getUsername(), request.getState());
            return LoginResponse.builder()
                .success(false)
                .error("Invalid credentials or account not found in this state")
                .build();
        }
        
        User user = userOpt.get();
        
        // Convert both states to backend format for comparison
        String userBackendState = convertToBackendStateFormat(user.getLocationState());
        String requestedBackendState = convertToBackendStateFormat(request.getState());
        
        // Validate user belongs to the requested state (flexible comparison)
        if (!userBackendState.equals(requestedBackendState)) {
            log.warn("❌ State mismatch - User state: {} ({}), Requested state: {} ({})", 
                user.getLocationState(), userBackendState, request.getState(), requestedBackendState);
            return LoginResponse.builder()
                .success(false)
                .error("Invalid credentials or account not found in this state")
                .build();
        }
        
        // Update last login
        userService.updateLastLogin(user, "mobile");
        
        // Generate mobile session token
        String token = generateMobileToken(user, requestedBackendState);
        
        log.info("✅ Mobile authentication successful for: {} ({}) in state: {}", 
            user.getUsername(), user.getUserRole(), request.getState());
        
        return LoginResponse.builder()
            .success(true)
            .data(LoginResponse.LoginData.builder()
                .user(buildUserInfo(user))
                .token(token)
                .authenticatedFrom(getStateDatabaseName(request.getState()))
                .build())
            .build();
    }
    
    /**
     * Validate mobile token and extract user information
     */
    public Optional<User> validateMobileToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Optional.empty();
            }
            
            String tokenData = authHeader.replace("Bearer ", "");
            String decoded = new String(Base64.getDecoder().decode(tokenData), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            
            if (parts.length < 4) {
                return Optional.empty();
            }
            
            Long userId = Long.parseLong(parts[0]);
            String username = parts[1];
            String state = parts[2];
            long timestamp = Long.parseLong(parts[3]);
            
            // Check token age (24 hours max)
            long tokenAge = System.currentTimeMillis() - timestamp;
            long maxAge = 24 * 60 * 60 * 1000; // 24 hours
            
            if (tokenAge > maxAge) {
                log.warn("⏰ Token expired for user: {}", username);
                return Optional.empty();
            }
            
            // Validate user still exists and is active
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isEmpty() || !userOpt.get().isAccountActive()) {
                log.warn("❌ User not found or inactive: {}", username);
                return Optional.empty();
            }
            
            User user = userOpt.get();
            if (!user.getUsername().equals(username)) {
                log.warn("❌ Username mismatch in token for user: {}", username);
                return Optional.empty();
            }
            
            return Optional.of(user);
            
        } catch (Exception e) {
            log.warn("❌ Invalid mobile token format", e);
            return Optional.empty();
        }
    }
    
    // Private helper methods
    
    private String generateWebToken(User user) {
        // Simple token format matching Node.js: Bearer base64(userId:username:timestamp)
        String tokenData = String.format("%d:%s:%d", 
            user.getId(), user.getUsername(), System.currentTimeMillis());
        String encoded = Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + encoded;  // Include Bearer prefix for consistency
    }
    
    private String generateMobileToken(User user, String state) {
        // Mobile token format matching Node.js: Bearer base64(userId:username:state:timestamp)
        String tokenData = String.format("%d:%s:%s:%d", 
            user.getId(), user.getUsername(), state, System.currentTimeMillis());
        String encoded = Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + encoded;  // Include Bearer prefix as expected by MobileTokenUtil
    }
    
    private LoginResponse.UserInfo buildUserInfo(User user) {
        return LoginResponse.UserInfo.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .userRole(user.getUserRole().name())
            .locationState(user.getLocationState())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .mobileNumber(user.getMobileNumber())
            .build();
    }
    
    /**
     * Dynamic state validation - can be enhanced to check actual available databases
     */
    private boolean isValidState(String state) {
        // For now, accept any non-empty state - actual validation happens during authentication
        return state != null && !state.trim().isEmpty();
    }
    
    /**
     * Convert state name to backend API format (removes spaces, normalizes case)
     * Dynamic conversion based on common patterns
     */
    private String convertToBackendStateFormat(String state) {
        if (state == null || state.trim().isEmpty()) {
            // Try to find user's state or use a reasonable default
            return "Karnataka"; // This could be made configurable
        }
        
        String normalized = state.trim();
        
        // Handle spaces by removing them and capitalizing properly
        if (normalized.contains(" ")) {
            // Convert "Andhra Pradesh" -> "AndhraPradesh", "Tamil Nadu" -> "TamilNadu"
            String[] parts = normalized.split("\\s+");
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    result.append(part.substring(0, 1).toUpperCase())
                          .append(part.substring(1).toLowerCase());
                }
            }
            return result.toString();
        }
        
        // Handle already formatted states - just normalize case
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1).toLowerCase();
    }
    
    private String getStateDatabaseName(String state) {
        String normalized = state.toLowerCase().replaceAll("[\\s\\-_]", "");
        switch (normalized) {
            case "andhrapradesh":
            case "ap":
                return "DataSync_AndhraPradesh";
            case "karnataka":
            case "ka":
                return "DataSync_Karnataka";
            case "tamilnadu":
            case "tn":
                return "DataSync_TamilNadu";
            default:
                return "DataSync";
        }
    }
}
