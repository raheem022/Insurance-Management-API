package com.insurance.management.controller;

import com.insurance.management.service.MetricsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metrics Controller for admin dashboard
 * Provides dashboard metrics endpoints matching the Node.js admin API
 */
@RestController
@RequestMapping("/admin/metrics")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class MetricsController {
    
    private final MetricsService metricsService;
    
    /**
     * GET /api/admin/metrics/overview
     * Get global overview metrics
     * Matches Node.js implementation exactly
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverviewMetrics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        log.info("📊 Admin overview metrics request - From: {}, To: {}", from, to);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> overviewData = metricsService.getOverviewMetrics(from, to);
            
            response.put("success", true);
            response.put("data", overviewData);
            
            log.info("✅ Overview metrics fetched successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching overview metrics", e);
            response.put("success", false);
            response.put("error", "Failed to fetch overview metrics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/admin/metrics/state
     * Get state-specific metrics
     * Matches Node.js implementation exactly
     */
    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getStateMetrics(
            @RequestParam String state,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        
        log.info("📍 Admin state metrics request for: {}", state);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (state == null || state.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "State parameter is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> stateData = metricsService.getStateMetrics(state, from, to);
            
            response.put("success", true);
            response.put("data", List.of(stateData)); // Node.js returns array with single object
            
            log.info("✅ State metrics fetched successfully for: {}", state);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching state metrics for: {}", state, e);
            response.put("success", false);
            response.put("error", "Failed to fetch state metrics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/admin/metrics/daily
     * Get daily progress trend data for the last N days
     * Matches Node.js implementation exactly
     */
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> getDailyMetrics(
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "7") Integer days) {
        
        log.info("📅 Admin daily metrics request - State: {}, Days: {}", state, days);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> dailyData = metricsService.getDailyMetrics(state, days);
            
            response.put("success", true);
            response.put("data", dailyData);
            
            log.info("✅ Daily metrics fetched successfully - {} days", dailyData.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching daily metrics", e);
            response.put("success", false);
            response.put("error", "Failed to fetch daily metrics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/admin/metrics/states
     * Get available states
     * Matches Node.js implementation exactly
     */
    @GetMapping("/states")
    public ResponseEntity<Map<String, Object>> getAvailableStates() {
        
        log.info("🗺️ Admin available states request");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> states = metricsService.getAvailableStates();
            
            response.put("success", true);
            response.put("data", states);
            
            log.info("✅ Available states fetched successfully - {} states", states.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching available states", e);
            response.put("success", false);
            response.put("error", "Failed to fetch states");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * GET /api/admin/metrics/users  
     * Get user-related metrics
     * Additional endpoint for user statistics
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserMetrics() {
        
        log.info("👥 Admin user metrics request");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> userMetrics = metricsService.getUserMetrics();
            
            response.put("success", true);
            response.put("data", userMetrics);
            
            log.info("✅ User metrics fetched successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error fetching user metrics", e);
            response.put("success", false);
            response.put("error", "Failed to fetch user metrics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
