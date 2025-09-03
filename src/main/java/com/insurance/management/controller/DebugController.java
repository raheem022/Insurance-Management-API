package com.insurance.management.controller;

import com.insurance.management.entity.User;
import com.insurance.management.service.UserService;
import com.insurance.management.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Debug Controller for development and troubleshooting
 * Provides debug endpoints matching the Node.js debug functionality
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class DebugController {
    
    private final UserService userService;
    private final CustomerService customerService;
    
    /**
     * GET /api/debug/assignments
     * Debug endpoint to check user assignments
     * Matches Node.js implementation exactly
     */
    @GetMapping("/assignments")
    public ResponseEntity<Map<String, Object>> getAssignmentDebugInfo() {
        
        log.info("🔍 Debug assignments request received");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get all active users
            List<User> activeUsers = userService.getActiveUsers();
            
            // Convert users to debug format
            List<Map<String, Object>> usersDebugInfo = activeUsers.stream()
                .map(user -> {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", user.getId());
                    userInfo.put("username", user.getUsername());
                    userInfo.put("location_state", user.getLocationState() != null ? user.getLocationState() : "");
                    
                    // Get customer assignment counts
                    try {
                        // TODO: Implement actual customer count queries when CustomerService methods are available
                        userInfo.put("assigned_customers", 0); // Placeholder
                        userInfo.put("completed_customers", 0); // Placeholder
                    } catch (Exception e) {
                        userInfo.put("assigned_customers", 0);
                        userInfo.put("completed_customers", 0);
                    }
                    
                    return userInfo;
                })
                .collect(Collectors.toList());
            
            // Build debug response matching Node.js format
            Map<String, Object> debugData = new HashMap<>();
            debugData.put("active_users", usersDebugInfo);
            
            // Add system info
            debugData.put("system_info", Map.of(
                "total_active_users", activeUsers.size(),
                "users_with_assignments", usersDebugInfo.stream()
                    .mapToLong(user -> (Integer) user.get("assigned_customers") > 0 ? 1 : 0).sum(),
                "timestamp", System.currentTimeMillis()
            ));
            
            response.put("success", true);
            response.put("data", debugData);
            
            log.info("✅ Debug assignments data fetched - {} active users", activeUsers.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching debug assignments", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/debug/health
     * System health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        
        log.info("🏥 Debug health check request");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Check database connectivity
            boolean dbHealthy = true;
            String dbStatus = "connected";
            
            try {
                userService.getTotalUserCount();
            } catch (Exception e) {
                dbHealthy = false;
                dbStatus = "disconnected: " + e.getMessage();
            }
            
            // System metrics
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            Map<String, Object> healthData = new HashMap<>();
            healthData.put("status", dbHealthy ? "healthy" : "unhealthy");
            healthData.put("database", dbStatus);
            healthData.put("memory", Map.of(
                "used_mb", usedMemory / 1024 / 1024,
                "free_mb", freeMemory / 1024 / 1024,
                "total_mb", totalMemory / 1024 / 1024,
                "max_mb", maxMemory / 1024 / 1024
            ));
            healthData.put("timestamp", System.currentTimeMillis());
            
            response.put("success", true);
            response.put("data", healthData);
            
            HttpStatus status = dbHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            log.info("✅ System health check completed - Status: {}", dbHealthy ? "healthy" : "unhealthy");
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            log.error("❌ Error during health check", e);
            response.put("success", false);
            response.put("error", "Health check failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/debug/database
     * Database connection and statistics debug info
     */
    @GetMapping("/database")  
    public ResponseEntity<Map<String, Object>> getDatabaseDebugInfo() {
        
        log.info("🗄️ Database debug info request");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get database statistics
            long totalUsers = userService.getTotalUserCount();
            long activeUsers = userService.getActiveUserCount();
            
            // TODO: Add customer statistics when available
            // long totalCustomers = customerService.getTotalCustomerCount();
            // long assignedCustomers = customerService.getAssignedCustomerCount();
            
            Map<String, Object> dbStats = new HashMap<>();
            dbStats.put("users", Map.of(
                "total", totalUsers,
                "active", activeUsers,
                "inactive", totalUsers - activeUsers
            ));
            
            // Add placeholder customer stats
            dbStats.put("customers", Map.of(
                "total", 0, // TODO: Replace with actual count
                "assigned", 0, // TODO: Replace with actual count
                "unassigned", 0 // TODO: Replace with actual count
            ));
            
            dbStats.put("connection_status", "active");
            dbStats.put("timestamp", System.currentTimeMillis());
            
            response.put("success", true);
            response.put("data", dbStats);
            
            log.info("✅ Database debug info fetched");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching database debug info", e);
            response.put("success", false);
            response.put("error", "Failed to fetch database info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
