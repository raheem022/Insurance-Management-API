package com.insurance.management.controller;

import com.insurance.management.config.GlobalErrorHandling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Health and System Information Controller
 * Provides system status endpoints matching the Node.js implementation
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class HealthController {
    
    private final GlobalErrorHandling.SystemHealthMonitor healthMonitor;
    
    /**
     * GET / - Root endpoint
     * Matches Node.js root route exactly
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> getApiInfo() {
        
        log.info("🏠 Root API info request received");
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Insurance Management API Server is running");
        response.put("status", "online");
        response.put("endpoints", Arrays.asList(
            "/health",
            "/api/auth/login",
            "/api/auth/mobile-login",
            "/api/auth/validate-token",
            "/api/auth/states",
            "/api/admin/users",
            "/api/admin/users/sync",
            "/api/admin/metrics/overview",
            "/api/admin/metrics/state",
            "/api/admin/metrics/daily",
            "/api/admin/metrics/states",
            "/api/admin/metrics/users",
            "/api/mobile/customers/allocated",
            "/api/mobile/customers/follow-up",
            "/api/debug/assignments",
            "/api/debug/health",
            "/api/debug/database"
        ));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /health - Health check endpoint
     * Matches Node.js health check exactly
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        
        log.info("🏥 Health check request received");
        
        try {
            Map<String, Object> healthMetrics = healthMonitor.getHealthMetrics();
            
            // Determine overall health status
            String status = "healthy";
            if (healthMetrics.containsKey("memory")) {
                Map<String, Object> memory = (Map<String, Object>) healthMetrics.get("memory");
                Number memoryUsageNum = (Number) memory.get("usage_percent");
                int memoryUsage = memoryUsageNum.intValue();
                if (memoryUsage > 90) {
                    status = "unhealthy";
                } else if (memoryUsage > 80) {
                    status = "warning";
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", status);
            response.put("database", "connected"); // TODO: Check actual database status
            response.put("timestamp", System.currentTimeMillis());
            response.put("uptime", System.currentTimeMillis()); // TODO: Calculate actual uptime
            response.put("version", "1.0.0");
            response.put("environment", System.getProperty("spring.profiles.active", "development"));
            
            // Add detailed metrics in development
            String env = System.getProperty("spring.profiles.active", "development");
            if ("development".equals(env)) {
                response.put("detailed_metrics", healthMetrics);
            }
            
            log.info("✅ Health check completed - Status: {}", status);
            
            // Return appropriate HTTP status
            if ("unhealthy".equals(status)) {
                return ResponseEntity.status(503).body(response); // Service Unavailable
            } else if ("warning".equals(status)) {
                return ResponseEntity.status(200).body(response); // OK but with warning
            } else {
                return ResponseEntity.ok(response);
            }
            
        } catch (Exception e) {
            log.error("❌ Health check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "unhealthy");
            errorResponse.put("database", "disconnected");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * GET /status - Extended status endpoint
     * Additional endpoint for detailed system information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getExtendedStatus() {
        
        log.info("📊 Extended status request received");
        
        try {
            Map<String, Object> healthMetrics = healthMonitor.getHealthMetrics();
            
            // System information
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("java_version", System.getProperty("java.version"));
            systemInfo.put("os_name", System.getProperty("os.name"));
            systemInfo.put("os_version", System.getProperty("os.version"));
            systemInfo.put("processors", runtime.availableProcessors());
            systemInfo.put("max_memory_mb", runtime.maxMemory() / 1024 / 1024);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "running");
            response.put("system", systemInfo);
            response.put("health", healthMetrics);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Extended status check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
