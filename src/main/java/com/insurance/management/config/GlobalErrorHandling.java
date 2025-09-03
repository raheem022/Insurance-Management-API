package com.insurance.management.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global Error Handling and Monitoring Configuration
 * Implements comprehensive exception handling and monitoring similar to Node.js
 */
@Configuration
@EnableScheduling
@Slf4j
public class GlobalErrorHandling {
    
    @Value("${spring.profiles.active:development}")
    private String activeProfile;
    
    /**
     * Global Exception Handler
     * Catches all unhandled exceptions similar to Node.js global error handlers
     */
    @ControllerAdvice
    @Slf4j
    public static class GlobalExceptionHandler {
        
        @Value("${spring.profiles.active:development}")
        private String activeProfile;
        
        /**
         * Handle validation errors
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidationExceptions(
                MethodArgumentNotValidException ex, HttpServletRequest request) {
            
            Map<String, Object> response = new HashMap<>();
            Map<String, String> errors = new HashMap<>();
            
            ex.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
            
            response.put("success", false);
            response.put("error", "Validation failed");
            response.put("details", errors);
            
            logError("VALIDATION_ERROR", ex, request);
            
            return ResponseEntity.badRequest().body(response);
        }
        
        /**
         * Handle database access errors
         */
        @ExceptionHandler(DataAccessException.class)
        public ResponseEntity<Map<String, Object>> handleDatabaseExceptions(
                DataAccessException ex, HttpServletRequest request) {
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            
            if ("production".equals(activeProfile)) {
                response.put("error", "Database error - please try again later");
            } else {
                response.put("error", "Database error: " + ex.getMessage());
            }
            
            logError("DATABASE_ERROR", ex, request);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        /**
         * Handle custom runtime exceptions
         */
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, Object>> handleRuntimeExceptions(
                RuntimeException ex, HttpServletRequest request) {
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            
            if ("production".equals(activeProfile)) {
                response.put("error", "Internal server error - please try again later");
            } else {
                response.put("error", ex.getMessage());
            }
            
            logError("RUNTIME_ERROR", ex, request);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        /**
         * Handle all other exceptions
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGenericException(
                Exception ex, HttpServletRequest request) {
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            
            if ("production".equals(activeProfile)) {
                response.put("error", "Internal server error - please try again later");
            } else {
                response.put("error", ex.getMessage());
            }
            
            logError("GENERAL_ERROR", ex, request);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        private void logError(String errorType, Exception ex, HttpServletRequest request) {
            log.error("🚨 {} - Path: {}, Method: {}, IP: {}, Error: {}", 
                errorType,
                request.getRequestURI(),
                request.getMethod(),
                getClientIpAddress(request),
                ex.getMessage(),
                ex
            );
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
    
    /**
     * System Health Monitor
     * Monitors system health similar to Node.js health checks
     */
    @Component
    @Slf4j
    public static class SystemHealthMonitor {
        
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong requestCount = new AtomicLong(0);
        private LocalDateTime lastHealthCheck = LocalDateTime.now();
        
        /**
         * Periodic health check - runs every 2 minutes
         */
        @Scheduled(fixedRate = 120000) // 2 minutes
        public void performHealthCheck() {
            try {
                // Check memory usage
                Runtime runtime = Runtime.getRuntime();
                long maxMemory = runtime.maxMemory();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;
                
                double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
                
                if (memoryUsagePercent > 80) {
                    log.warn("⚠️ HIGH MEMORY USAGE: {:.1f}%", memoryUsagePercent);
                    
                    if (memoryUsagePercent > 90) {
                        log.warn("🧹 Attempting garbage collection...");
                        System.gc();
                    }
                }
                
                lastHealthCheck = LocalDateTime.now();
                
                // Log periodic health status
                if (log.isDebugEnabled()) {
                    log.debug("✅ Health check completed - Memory: {:.1f}%, Errors: {}, Requests: {}",
                        memoryUsagePercent, errorCount.get(), requestCount.get());
                }
                
            } catch (Exception e) {
                log.error("❌ Health check failed", e);
            }
        }
        
        /**
         * Increment error count
         */
        public void incrementErrorCount() {
            errorCount.incrementAndGet();
        }
        
        /**
         * Increment request count
         */
        public void incrementRequestCount() {
            requestCount.incrementAndGet();
        }
        
        /**
         * Get current health metrics
         */
        public Map<String, Object> getHealthMetrics() {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("status", "healthy");
            metrics.put("timestamp", System.currentTimeMillis());
            metrics.put("uptime", System.currentTimeMillis());
            metrics.put("memory", Map.of(
                "used_mb", usedMemory / 1024 / 1024,
                "free_mb", freeMemory / 1024 / 1024,
                "total_mb", totalMemory / 1024 / 1024,
                "max_mb", maxMemory / 1024 / 1024,
                "usage_percent", Math.round((double) usedMemory / maxMemory * 100)
            ));
            metrics.put("requests", Map.of(
                "total", requestCount.get(),
                "errors", errorCount.get(),
                "success_rate", requestCount.get() > 0 ? 
                    Math.round((1.0 - (double) errorCount.get() / requestCount.get()) * 100) : 100
            ));
            metrics.put("last_health_check", lastHealthCheck.toString());
            
            return metrics;
        }
    }
    
    /**
     * Database Health Indicator
     * Custom health indicator for database connectivity
     */
    @Component
    public static class DatabaseHealthIndicator implements HealthIndicator {
        
        private final DataSource dataSource;
        
        public DatabaseHealthIndicator(DataSource dataSource) {
            this.dataSource = dataSource;
        }
        
        @Override
        public Health health() {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    return Health.up()
                        .withDetail("database", "accessible")
                        .withDetail("connection_timeout", "5s")
                        .build();
                } else {
                    return Health.down()
                        .withDetail("database", "connection_invalid")
                        .build();
                }
            } catch (Exception ex) {
                return Health.down()
                    .withDetail("database", "connection_failed")
                    .withDetail("error", ex.getMessage())
                    .build();
            }
        }
    }
    
    /**
     * Memory Cleanup Configuration
     * Similar to Node.js garbage collection hints
     */
    @Component
    @Slf4j
    public static class MemoryManager {
        
        private static final long MEMORY_THRESHOLD = 500 * 1024 * 1024; // 500MB
        private int memoryWarningCount = 0;
        
        /**
         * Monitor memory usage every 30 seconds
         */
        @Scheduled(fixedRate = 30000) // 30 seconds
        public void monitorMemory() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            
            if (usedMemory > MEMORY_THRESHOLD) {
                memoryWarningCount++;
                log.warn("⚠️ HIGH MEMORY USAGE: {}MB", usedMemory / 1024 / 1024);
                
                if (memoryWarningCount > 5) {
                    log.warn("🧹 Attempting garbage collection...");
                    System.gc();
                    memoryWarningCount = 0;
                }
            } else {
                memoryWarningCount = 0;
            }
        }
    }
}
