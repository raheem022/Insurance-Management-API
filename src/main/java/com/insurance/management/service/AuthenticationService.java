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
    
    // Valid states for mobile authentication
    private static final List<String> VALID_STATES = Arrays.asList(
        "Karnataka", "TamilNadu", "AndhraPradesh", "Andhra Pradesh"
    );
    
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
     * Authenticate mobile user with state-specific logic
     * Matches POST /api/auth/mobile-login from Node.js
     */
    public LoginResponse authenticateMobileUser(LoginRequest request) {
        log.info("📱 Mobile authentication attempt - Username: {}, State: {}", 
            request.getUsername(), request.getState());
        
        try {
            // Validate required fields for mobile login
            if (request.getState() == null || request.getState().trim().isEmpty()) {
                return LoginResponse.builder()
                    .success(false)
                    .error("Username, PIN, and state are required for mobile login")
                    .build();
            }
            
            // Validate state
            if (!isValidState(request.getState())) {
                return LoginResponse.builder()
                    .success(false)
                    .error("Invalid state or state database not available: " + request.getState())
                    .build();
            }
            
            // TODO: Implement state-specific database authentication
            // For now, authenticate against main database and validate state
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
            
            // Validate user belongs to the requested state
            if (user.getLocationState() == null || !user.getLocationState().equals(request.getState())) {
                log.warn("❌ State mismatch - User state: {}, Requested state: {}", 
                    user.getLocationState(), request.getState());
                return LoginResponse.builder()
                    .success(false)
                    .error("Invalid credentials or account not found in this state")
                    .build();
            }
            
            // Update last login
            userService.updateLastLogin(user, "mobile");
            
            // Generate mobile session token (matching Node.js format)
            String token = generateMobileToken(user, request.getState());
            
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
        // Simple token format matching Node.js: base64(userId:username:timestamp)
        String tokenData = String.format("%d:%s:%d", 
            user.getId(), user.getUsername(), System.currentTimeMillis());
        return Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
    }
    
    private String generateMobileToken(User user, String state) {
        // Mobile token format matching Node.js: base64(userId:username:state:timestamp)
        String tokenData = String.format("%d:%s:%s:%d", 
            user.getId(), user.getUsername(), state, System.currentTimeMillis());
        return Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
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
    
    private boolean isValidState(String state) {
        return VALID_STATES.contains(state);
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
