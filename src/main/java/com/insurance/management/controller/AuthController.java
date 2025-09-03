package com.insurance.management.controller;

import com.insurance.management.dto.LoginRequest;
import com.insurance.management.dto.LoginResponse;
import com.insurance.management.service.AuthenticationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 * Provides login endpoints matching the Node.js server implementation
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    
    private final AuthenticationService authenticationService;
    
    /**
     * POST /api/auth/login (due to context path /api + controller /auth)
     * Web/Admin authentication endpoint
     * Matches the Node.js implementation exactly
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("🌐 Web login attempt from IP: {} for username: {}", 
            getClientIpAddress(httpRequest), request.getUsername());
        
        try {
            LoginResponse response = authenticationService.authenticateWebUser(request);
            
            HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
            
            if (response.isSuccess()) {
                log.info("✅ Web login successful for user: {}", request.getUsername());
            } else {
                log.warn("❌ Web login failed for user: {} - {}", request.getUsername(), response.getError());
            }
            
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            log.error("❌ Web login error for user: {}", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(LoginResponse.builder()
                    .success(false)
                    .error("Login failed")
                    .build());
        }
    }
    
    /**
     * POST /api/auth/mobile-login (due to context path /api + controller /auth)
     * Mobile app authentication endpoint with state-specific routing
     * Matches the Node.js implementation exactly
     */
    @PostMapping("/mobile-login")
    public ResponseEntity<LoginResponse> mobileLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("📱 Mobile login attempt from IP: {} for username: {} in state: {}", 
            getClientIpAddress(httpRequest), request.getUsername(), request.getState());
        
        try {
            LoginResponse response = authenticationService.authenticateMobileUser(request);
            
            HttpStatus status;
            if (response.isSuccess()) {
                status = HttpStatus.OK;
                log.info("✅ Mobile login successful for user: {} in state: {}", 
                    request.getUsername(), request.getState());
            } else {
                // Determine appropriate HTTP status based on error
                if (response.getError().contains("required") || response.getError().contains("Invalid state")) {
                    status = HttpStatus.BAD_REQUEST;
                } else {
                    status = HttpStatus.UNAUTHORIZED;
                }
                log.warn("❌ Mobile login failed for user: {} in state: {} - {}", 
                    request.getUsername(), request.getState(), response.getError());
            }
            
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            log.error("❌ Mobile login error for user: {} in state: {}", 
                request.getUsername(), request.getState(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(LoginResponse.builder()
                    .success(false)
                    .error("Mobile login failed")
                    .build());
        }
    }
    
    /**
     * POST /api/auth/validate-token
     * Token validation endpoint for mobile apps
     */
    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(HttpServletRequest request) {
        
        String authHeader = request.getHeader("Authorization");
        log.info("🔍 Token validation request from IP: {}", getClientIpAddress(request));
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            var userOpt = authenticationService.validateMobileToken(authHeader);
            
            if (userOpt.isPresent()) {
                var user = userOpt.get();
                response.put("success", true);
                response.put("user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "user_role", user.getUserRole().name(),
                    "location_state", user.getLocationState() != null ? user.getLocationState() : "",
                    "first_name", user.getFirstName() != null ? user.getFirstName() : "",
                    "last_name", user.getLastName() != null ? user.getLastName() : ""
                ));
                log.info("✅ Token validation successful for user: {}", user.getUsername());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Invalid or expired token");
                log.warn("❌ Token validation failed");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
        } catch (Exception e) {
            log.error("❌ Token validation error", e);
            response.put("success", false);
            response.put("error", "Token validation failed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/auth/states
     * Get available states for mobile login
     */
    @GetMapping("/states")
    public ResponseEntity<Map<String, Object>> getAvailableStates() {
        log.info("📋 Available states requested");
        
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", java.util.Arrays.asList(
                "Karnataka", "TamilNadu", "AndhraPradesh", "Andhra Pradesh"
            ));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error fetching available states", e);
            response.put("success", false);
            response.put("error", "Failed to fetch available states");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // Helper methods
    
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
